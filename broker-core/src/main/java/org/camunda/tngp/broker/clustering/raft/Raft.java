package org.camunda.tngp.broker.clustering.raft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.controller.ConfigureController;
import org.camunda.tngp.broker.clustering.raft.controller.JoinController;
import org.camunda.tngp.broker.clustering.raft.controller.LeaveController;
import org.camunda.tngp.broker.clustering.raft.controller.PollController;
import org.camunda.tngp.broker.clustering.raft.controller.ReplicationController;
import org.camunda.tngp.broker.clustering.raft.controller.VoteController;
import org.camunda.tngp.broker.clustering.raft.handler.RaftMessageHandler;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureRequest;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.raft.message.LeaveRequest;
import org.camunda.tngp.broker.clustering.raft.message.LeaveResponse;
import org.camunda.tngp.broker.clustering.raft.message.PollRequest;
import org.camunda.tngp.broker.clustering.raft.message.PollResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.state.ActiveState;
import org.camunda.tngp.broker.clustering.raft.state.CandidateState;
import org.camunda.tngp.broker.clustering.raft.state.FollowerState;
import org.camunda.tngp.broker.clustering.raft.state.InactiveState;
import org.camunda.tngp.broker.clustering.raft.state.LeaderState;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.broker.clustering.raft.state.RaftState;
import org.camunda.tngp.logstreams.log.LogStream;

public class Raft implements Agent
{
    private final RaftContext context;
    private final LogStream stream;
    private final MetaStore meta;

    private final InactiveState inactiveState;
    private final FollowerState followerState;
    private final CandidateState candidateState;
    private final LeaderState leaderState;

    private volatile int term = 0;

    private boolean lastVotedForAvailable;
    private final Endpoint lastVotedFor;

    private boolean leaderAvailable;
    private final Endpoint leader;

    private long commitPosition = -1L;

    protected long lastContact;

    private final Member member;
    private List<Member> members;

    private volatile RaftState state;
    private Configuration configuration;

    private final JoinController joinController;
    private final LeaveController leaveController;

    private final RaftMessageHandler fragmentHandler;

    private final List<Consumer<Raft>> stateChangeListeners;

    public Raft(final RaftContext context, final LogStream stream, final MetaStore meta)
    {
        this.context = context;
        this.stream = stream;
        this.meta = meta;

        final LogStreamState logStreamState = new LogStreamState(stream);

        context.setRaft(this);
        context.setLogStreamState(logStreamState);

        this.lastVotedForAvailable = false;
        this.lastVotedFor = new Endpoint();

        this.leaderAvailable = false;
        this.leader = new Endpoint();

        this.member = new Member(context.getRaftEndpoint(), context);
        this.members = new CopyOnWriteArrayList<>();

        this.inactiveState = new InactiveState(context);
        this.followerState = new FollowerState(context);
        this.candidateState = new CandidateState(context);
        this.leaderState = new LeaderState(context);

        this.state = inactiveState;

        this.fragmentHandler = new RaftMessageHandler(context, context.getSubscription());
        this.joinController = new JoinController(context);
        this.leaveController = new LeaveController(context);

        this.stateChangeListeners = new CopyOnWriteArrayList<>();

        this.term = meta.loadTerm();

        final Endpoint vote = meta.loadVote();
        if (vote != null)
        {
            lastVotedForAvailable = true;
            lastVotedFor.wrap(vote);
        }

        this.configuration = meta.loadConfiguration();

        if (configuration != null)
        {
            final List<Member> members = configuration.members();
            for (int i = 0; i < members.size(); i++)
            {
                final Member m = members.get(i);
                this.members.add(new Member(m.endpoint(), context));
            }
        }
    }


    @Override
    public String roleName()
    {
        return String.format("raft.%d", stream.getId());
    }

    @Override
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += fragmentHandler.doWork();
        workcount += state.doWork();
        workcount += joinController.doWork();
        workcount += leaveController.doWork();

        return workcount;
    }

    public void close()
    {
        this.state.close();

        while (!this.state.isClosed())
        {
            this.state.doWork();
        }
    }

    public int id()
    {
        return stream.getId();
    }

    public LogStream stream()
    {
        return stream;
    }

    public MetaStore meta()
    {
        return meta;
    }

    public Configuration configuration()
    {
        return configuration;
    }

    public State state()
    {
        return state.state();
    }

    public boolean needMembers()
    {
        return isLeader(); // TODO: && not enough members;
    }

    public boolean isLeader()
    {
        return state.state() == State.LEADER;
    }

    public int term()
    {
        return term;
    }

    public Raft term(final int term)
    {
        if (term > this.term)
        {
            this.term = term;
            leader(null);
            lastVotedFor(null);
            meta.storeTermAndVote(term, null);
        }
        return this;
    }

    public long commitPosition()
    {
        return commitPosition;
    }

    public Raft commitPosition(final long commitPosition)
    {
        final long previousCommitPosition = this.commitPosition;
        if (previousCommitPosition < commitPosition)
        {
            this.commitPosition = commitPosition;
            final long configurationPosition = configuration().configurationEntryPosition();
            if (configurationPosition > previousCommitPosition && configurationPosition <= commitPosition)
            {
                meta.storeConfiguration(configuration);
            }
        }

        return this;
    }

    public long lastContact()
    {
        return lastContact;
    }

    public Raft lastContact(final long lastContact)
    {
        this.lastContact = lastContact;
        return this;
    }

    public Endpoint leader()
    {
        return leaderAvailable ? leader : null;
    }

    public Raft leader(final Endpoint leader)
    {
        leaderAvailable = false;
        this.leader.reset();

        if (leader != null)
        {
            leaderAvailable = true;
            this.leader.wrap(leader);
        }

        return this;
    }

    public Endpoint lastVotedFor()
    {
        return lastVotedForAvailable ? lastVotedFor : null;
    }

    public Raft lastVotedFor(final Endpoint lastVotedFor)
    {
        lastVotedForAvailable = false;
        this.lastVotedFor.reset();

        if (lastVotedFor != null)
        {
            lastVotedForAvailable = true;
            this.lastVotedFor.wrap(lastVotedFor);
            meta.storeVote(lastVotedFor);
        }

        return this;
    }

    public Member member()
    {
        return member;
    }

    public List<Member> members()
    {
        return members;
    }

    public int quorum()
    {
        return (int) Math.floor((members().size() + 1) / 2.0) + 1;
    }

    public Member getMemberByEndpoint(final Endpoint endpoint)
    {
        for (int i = 0; i < members.size(); i++)
        {
            final Member member = members.get(i);
            if (member.endpoint().equals(endpoint))
            {
                return member;
            }
        }

        return null;
    }

    public Raft bootstrap()
    {
        if (configuration == null)
        {
            final List<Member> members = new CopyOnWriteArrayList<>();
            members.add(new Member(member.endpoint(), context));
            configure(new Configuration(0L, 0, members));
        }

        return join();
    }

    public Raft join(final List<Member> members)
    {
        if (configuration == null)
        {
            final List<Member> configuredMembers = new CopyOnWriteArrayList<>();
            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                if (!member.equals(this.member.endpoint()))
                {
                    configuredMembers.add(member);
                }
            }

            if (configuredMembers.isEmpty())
            {
                throw new IllegalStateException("cannot join empty cluster");
            }

            configure(new Configuration(0L, 0, configuredMembers));
        }

        return join();
    }

    public CompletableFuture<Void> leave()
    {
        final CompletableFuture<Void> future = new CompletableFuture<Void>();
        leaveController.open(future);
        return future;
    }

    protected Raft join()
    {
        transition(State.FOLLOWER);

        final List<Member> activeMembers = new CopyOnWriteArrayList<>();

        final int size = members.size();
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            if (!member.equals(member()))
            {
                activeMembers.add(member);
            }
        }

        if (!activeMembers.isEmpty())
        {
            joinController.open(activeMembers);
        }

        return this;
    }

    public Raft configure(final Configuration configuration)
    {
        if (this.configuration != null && (configuration.configurationEntryPosition() <= this.configuration.configurationEntryPosition()))
        {
            return this;
        }

        final List<Member> configuredMembers = configuration.members();

        for (int i = 0; i < configuredMembers.size(); i++)
        {
            final Member configuredMember = configuredMembers.get(i);

            if (!members.contains(configuredMember))
            {
                if (member.equals(configuredMember))
                {
                    members.add(member);
                }
                else
                {
                    members.add(new Member(configuredMember.endpoint(), context));
                }
            }
        }

        int i = 0;
        while (i < members.size())
        {
            final Member member = members.get(i);

            if (!configuredMembers.contains(member))
            {
                try
                {
                    final VoteController voteController = member.getVoteController();
                    voteController.closeForcibly();

                    final ReplicationController replicationController = member.getReplicationController();
                    replicationController.closeForcibly();

                    final PollController pollController = member.getPollController();
                    pollController.closeForcibly();

                    final ConfigureController configureController = member.getConfigureController();
                    configureController.closeForcibly();
                }
                finally
                {
                    members.remove(member);
                }
            }
            else
            {
                i++;
            }
        }

        this.configuration = configuration;

        if (commitPosition() >= configuration.configurationEntryPosition())
        {
            meta.storeConfiguration(this.configuration);
        }

        return this;
    }

    public void transition(final State state)
    {
        if (this.state.state() != state)
        {
//            System.out.println(String.format("Transitioning %s from %s to %s, %d", stream.getLogName(), this.state.state(), state, System.currentTimeMillis()));

            try
            {
                this.state.close();

                while (!this.state.isClosed())
                {
                    this.state.doWork();
                }
            }
            finally
            {
                this.state = getState(state);
                this.state.open();
            }

            for (int i = 0; i < stateChangeListeners.size(); i++)
            {
                stateChangeListeners.get(i).accept(this);
            }
        }
    }

    protected ActiveState getState(final State state)
    {
        switch (state)
        {
            case FOLLOWER:
                return followerState;
            case CANDIDATE:
                return candidateState;
            case LEADER:
                return leaderState;
            default:
                throw new IllegalArgumentException();
        }
    }

    public AppendResponse handleAppendRequest(final AppendRequest appendRequest)
    {
        return state.append(appendRequest);
    }

    public void handleAppendResponse(final AppendResponse appendResponse)
    {
        state.appended(appendResponse);
    }

    public PollResponse handlePollRequest(final PollRequest pollRequest)
    {
        return state.poll(pollRequest);
    }

    public VoteResponse handleVoteRequest(final VoteRequest voteRequest)
    {
        return state.vote(voteRequest);
    }

    public ConfigureResponse handleConfigureRequest(final ConfigureRequest configureRequest)
    {
        return state.configure(configureRequest);
    }

    public CompletableFuture<JoinResponse> handleJoinRequest(final JoinRequest joinRequest)
    {
        return state.join(joinRequest);
    }

    public CompletableFuture<LeaveResponse> handleLeaveRequest(final LeaveRequest leaveRequest)
    {
        return state.leave(leaveRequest);
    }

    public void onStateChange(Consumer<Raft> listener)
    {
        stateChangeListeners.add(listener);
    }

    public enum State
    {
        INACTIVE, FOLLOWER, CANDIDATE, LEADER
    }

}