package twp.commands;

import arc.func.Cons;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.gen.Player;
import twp.database.PD;
import twp.tools.Testing;
import twp.tools.Text;

import java.util.concurrent.locks.ReentrantLock;

import static twp.Main.*;

// Command is base class of any command and contains utility for making commands bit more cleaner and organised
// One good advice, dont write your game in java... newer.
public abstract class Command {
    private static final ReentrantLock lock1 = new ReentrantLock();

    public boolean freeAccess;
    Verifier verifier = (id) -> true;
    // constant
    public String name = "noname", argStruct, description = "description missing";

    // dynamic
    public Result result;
    public Object[] arg = {};
    public PD caller;

    // main behavior
    public abstract void run(String id, String ...args);

    void setArg(Object ... values) {
        arg = values;
    }

    // Shorthand for checking whether correct amount of arguments were provided
    boolean checkArgCount(int count, int supposed) {
        if (count < supposed) {
            setArg(count, supposed);
            result = Result.notEnoughArgs;
            return true;
        }
        return false;
    }

    // Shorthand for checking and handling invalid non integer arguments
    boolean isNotInteger(String[] args, int idx) {
        if(Strings.canParsePositiveInt(args[idx])) {
            return false;
        }
        setArg(idx + 1, args[idx]);
        result = Result.notInteger;
        return true;
    }

    // for registration of commandline commands
    public void registerCmp(CommandHandler handler, TerminalCommandRunner runner) {
        freeAccess = true;
        Cons<String[]> func = (args) -> new Thread(() -> {
            lock1.lock();

            try {
                run("", args);
                queue.post(() -> {
                    if (runner != null) {
                        runner.run(this);
                    } else {
                        notifyCaller();
                    }
                });
            } catch (Exception ex) {
                result = Result.bug;
                // fucking java and deadlocks
                try {
                    notifyCaller();
                } catch (Exception e) {
                    Testing.Log(ex);
                }
                Testing.Log(ex);
            }

            lock1.unlock();
        }).start();

        if (argStruct == null) {
            handler.register(name, description, func);
        } else  {
            handler.register(name, argStruct, description, func);
        }
    }

    // For registration of in-game commands
    public void registerGm(CommandHandler handler, PlayerCommandRunner runner) {
        CommandHandler.CommandRunner<Player> run = (args, player) -> new Thread(() -> {
            PD pd = db.online.get(player.uuid());

            if(pd == null) {
                Testing.Log("null PD when executing " + name + " command.");
                player.sendMessage("[yellow]Sorry there is an problem with your profile, " +
                        "try reconnecting or contact admins that you see this message.");
                return;
            }

            lock1.lock();
            caller = pd;

            try {
                run(player.uuid(), args);

                queue.post(() -> {
                    if (runner != null) {
                        runner.run(this, pd);
                    } else {
                        notifyCaller();
                    }
                });
            } catch (Exception ex) {
                result = Result.bug;
                // fucking java and deadlocks
                try {
                    notifyCaller();
                } catch (Exception e) {
                    Testing.Log(e);
                }
                Testing.Log(ex);
            }



            lock1.unlock();
        }).start();

        if (argStruct == null) {
            handler.register(name, description, run);
        } else  {
            handler.register(name, argStruct, description, run);
        }
    }

    // getMessage returns bundle key based of result
    public String getMessage() {
        return (result.general ? "" : (name + "-")) + result.name();
    }

    // notifyCaller sends message to caller, its just a shorthand and is atomaticly called if
    // command lambda is null
    public void notifyCaller() {
        if(caller == null) {
            Log.info(Text.cleanColors(Text.format(bundle.getDefault(getMessage()), arg)));
            return;
        }
        caller.sendServerMessage(getMessage(), arg);
    }

    // shorthand for kicking caller
    public void kickCaller(int duration) {
        caller.kick(getMessage(), duration, arg);
    }

    // creates string of information about online players
    public String listPlayers() {
        StringBuilder sb = new StringBuilder();
        db.online.forEachValue(iter -> {
            sb.append(iter.next().summarize()).append("\n");
        });
        return sb.substring(0, sb.length() -1);
    }

    boolean isPlayerAdmin(String id) {
        PD pd = db.online.get(id);
        return pd != null && pd.rank.admin;
    }

    void playerNotFound() {
        result = Result.playerNotFound;
        setArg(listPlayers());
    }

    boolean cannotInteract(String id) {
        if(db.online.get(id).cannotInteract()) {
            result = Result.noPerm;
            return true;
        }
        return false;
    }

    // Used for testing commands
    public void assertResult(Result supposed) {
        Log.info(Text.cleanColors(Text.format(bundle.getDefault(getMessage()), arg)));
        if(supposed != result) {
            throw new RuntimeException(supposed.name() + "!=" + result.name());
        }
    }

    // lambda for commands invoiced by players
    public interface PlayerCommandRunner {
        void run(Command c, PD pd);
    }

    // lambda for commands called from command prompt
    public interface TerminalCommandRunner {
        void run(Command c);
    }

    public interface Verifier {
        boolean verify(String id);
    }

    // Result enum contains all possible results command can return
    public enum Result {
        success,
        notFound,
        notExplicit,
        noPerm,
        wrongRank,
        wrongAccess,
        wrongOption,
        unprotectSuccess,
        alreadyProtected,
        confirm,
        confirmFail,
        confirmSuccess,
        invalidRequest,
        loginSuccess,
        unsetSuccess,
        emptySlice,
        invalidSlice,
        noOneOnline,
        successOnline,
        wrongCommand,
        redundant,
        cannotApplyToSelf,
        notExist,
        invalidFile,
        enableFail,
        addSuccess,
        alreadyEnabled,
        alreadyDisabled,
        disableFail,
        updateSuccess,
        updateFail,
        alreadyAdded,

        bug(true),
        notInteger(true),
        playerNotFound(true),
        notEnoughArgs(true),
        incorrectPassword(true),
        alreadyVoting(true),
        cannotVote(true),
        voteStartSuccess(true),
        invalidVoteSession(true),
        voteSuccess(true),
        alreadyVoted(true);

        boolean general;

        Result() {}

        Result(boolean general) {
            this.general = general;
        }
    }
}
