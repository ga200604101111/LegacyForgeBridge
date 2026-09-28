package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import java.util.*;

/** Bounded numeric/client-gesture projection. Does not call source classes, damage, spawn,
 * mutate inventory/NBT, or assume a result for an unknown source event. A DEFER instruction
 * ends the proven source prefix; gestures before that boundary remain source ordered. */
public final class ClientFeedbackProgram {
    private static final Set<String> OPS=Set.of("NOP","NULL","INT","FLOAT","DOUBLE","LOAD","STORE","IINC","POP","DUP","IADD","ISUB","IMUL","IDIV","IREM","FADD","FSUB","FMUL","FDIV","FREM","DADD","DSUB","DMUL","DDIV","DREM","INEG","FNEG","DNEG","I2F","D2F","I2D","F2D","F2I","D2I","FCMPL","FCMPG","DCMPL","DCMPG","GOTO","IFEQ","IFNE","IFLT","IFLE","IFGT","IFGE","IF_ICMPEQ","IF_ICMPNE","IF_ICMPLT","IF_ICMPLE","IF_ICMPGT","IF_ICMPGE","IFNULL","IFNONNULL","WORLD","REMOTE","DURATION","DAMAGE","MAX","EQUIPPED","SWING","BASE_USE_TICK","SERVER_RESOURCE","CALL","RETURN","IRETURN","FRETURN","DRETURN","ARETURN","DEFER");
    public record Step(String op,int arg,String text){}
    public record Code(int locals,int[] argumentSlots,boolean isStatic,List<Step> steps){
        public Code { argumentSlots=argumentSlots.clone();steps=List.copyOf(steps);
            if(locals<1||locals>256||steps.isEmpty()||steps.size()>8192||argumentSlots.length>32)throw new IllegalArgumentException("feedback code budget");
            for(int slot:argumentSlots)if(slot<0||slot>=locals)throw new IllegalArgumentException("argument slot");
            for(Step s:steps){if(s.op()==null||!OPS.contains(s.op())||s.op().length()>32||s.text()!=null&&s.text().length()>4096)throw new IllegalArgumentException("feedback instruction");if(s.op().equals("LOAD")||s.op().equals("STORE")||s.op().equals("IINC")){if(s.arg()<0||s.arg()>=locals)throw new IllegalArgumentException("local bound");}
                if(s.op().equals("SERVER_RESOURCE")&&(s.arg()<1||s.arg()>4))throw new IllegalArgumentException("resource stack count");
                if(s.op().equals("FLOAT")&&!Float.isFinite(Float.parseFloat(s.text()))||s.op().equals("DOUBLE")&&!Double.isFinite(Double.parseDouble(s.text())))throw new IllegalArgumentException("nonfinite feedback constant");
                if(s.op().equals("GOTO")||s.op().startsWith("IF")){if(s.arg()<0||s.arg()>=steps.size())throw new IllegalArgumentException("branch bound");}}
        }
        @Override public int[] argumentSlots(){return argumentSlots.clone();}
    }
    public record Program(String kind,String root,Map<String,Code> methods){
        public Program {methods=Map.copyOf(methods);if(!Set.of("release","entity","usingTick").contains(kind)||methods.size()>64||!methods.containsKey(root))throw new IllegalArgumentException("feedback program contract");}
    }
    public record Input(int duration,int remaining,int damage,int maxDamage){}
    public record Result(int swings,boolean completed,String deferredReason){}
    private enum Ref {SELF,STACK,WORLD,PLAYER,TARGET,UNKNOWN,NULL}
    private static class Boundary extends RuntimeException {Boundary(String why){super(why);}}
    private static final class Machine {final Program p;final Input in;int fuel=8192,swings;boolean serverResourceSeen;Machine(Program p,Input in){this.p=p;this.in=in;}}
    private ClientFeedbackProgram(){}
    public static Program parse(JsonObject json){
        int schema=json.get("schema").getAsBigDecimal().intValueExact();
        boolean tick="usingTick".equals(json.get("kind").getAsString());
        if(schema!=(tick?2:1))throw new IllegalArgumentException("feedback schema");
        if(tick&&(!"net/minecraftforge/event/entity/player/PlayerUseItemEvent$Tick".equals(text(json,"requiresEvent"))
                ||!"AFTER_EVENT_BEFORE_DECREMENT".equals(text(json,"eventPhase"))))
            throw new IllegalArgumentException("using tick event contract");
        JsonObject all=json.getAsJsonObject("methods");if(all==null||all.size()>64)throw new IllegalArgumentException("feedback method count");Map<String,Code> codes=new LinkedHashMap<>();
        for(var e:all.entrySet()){
            var o=e.getValue().getAsJsonObject();var a=o.getAsJsonArray("steps");if(a==null||a.size()>8192)throw new IllegalArgumentException("feedback steps");List<Step> steps=new ArrayList<>();
            for(var v:a){var i=v.getAsJsonObject();steps.add(new Step(i.get("op").getAsString(),i.get("arg").getAsBigDecimal().intValueExact(),i.has("text")&&!i.get("text").isJsonNull()?i.get("text").getAsString():null));}
            var slots=o.getAsJsonArray("argumentSlots");if(slots.size()>32)throw new IllegalArgumentException("arg count");int[] s=new int[slots.size()];for(int i=0;i<s.length;i++)s[i]=slots.get(i).getAsBigDecimal().intValueExact();
            codes.put(e.getKey(),new Code(o.get("locals").getAsBigDecimal().intValueExact(),s,o.get("isStatic").getAsBoolean(),steps));
        }
        Program p=new Program(json.get("kind").getAsString(),json.get("root").getAsString(),codes);
        for(Code c:codes.values())for(Step s:c.steps())if(s.op().equals("CALL")&&!codes.containsKey(s.text()))throw new IllegalArgumentException("missing feedback helper");return p;
    }
    private static String text(JsonObject json,String key){return json.has(key)&&json.get(key).isJsonPrimitive()?json.get(key).getAsString():null;}
    public static JsonObject encode(Program p){
        var j=new Gson().toJsonTree(p).getAsJsonObject();boolean tick="usingTick".equals(p.kind());
        j.addProperty("schema",tick?2:1);
        if(tick){j.addProperty("requiresEvent","net/minecraftforge/event/entity/player/PlayerUseItemEvent$Tick");j.addProperty("eventPhase","AFTER_EVENT_BEFORE_DECREMENT");}
        return j;
    }
    public static Result evaluate(Program p,Input in){
        if("usingTick".equals(p.kind()))return new Result(0,false,"upstream PlayerUseItemEvent.Tick result required");
        if(in.duration()<0||in.duration()>1_000_000||in.remaining()<0||in.remaining()>in.duration())throw new IllegalArgumentException("feedback use time");
        List<Object> args=p.kind().equals("release")?List.of(Ref.STACK,Ref.WORLD,Ref.PLAYER,in.remaining()):List.of(Ref.STACK,Ref.PLAYER,Ref.TARGET);
        return evaluateArguments(p,in,args);
    }
    /** Pure projection AFTER the caller has obtained the real upstream event result.
     * The optional count is the effective remaining time, before the 1.7.10 decrement,
     * NOT elapsed time and NOT a default-event permission. Empty always defers.
     * This API does not verify an event environment, dispatch events or enable native ticks.
     * Duration/damage/maxDamage must be a fresh POST-event source snapshot for the
     * still-active stack and its matching source class; Input.remaining is the earlier
     * observation and is deliberately not used as the callback count. Cancellation,
     * stack replacement and player/world changes must be resolved by the caller.
     * Callers must not substitute the original count when upstream behavior is unresolved.
     */
    public static Result evaluateUsingTick(Program p,Input in,OptionalInt remainingAfterEvent){
        if(!"usingTick".equals(p.kind()))throw new IllegalArgumentException("not a using tick program");
        Objects.requireNonNull(remainingAfterEvent,"upstream tick result");
        if(remainingAfterEvent.isEmpty())return new Result(0,false,"upstream PlayerUseItemEvent.Tick result unresolved");
        Code root=p.methods().get(p.root());
        if(root.isStatic()||root.argumentSlots.length!=3)throw new IllegalArgumentException("using tick root signature");
        if(in.duration()<0||in.duration()>1_000_000||in.remaining()<0||in.remaining()>1_000_000)throw new IllegalArgumentException("feedback use time");
        int remaining=remainingAfterEvent.getAsInt();
        // Forge calls finish, not onUsingTick, if the event cancels or returns <= 0.
        if(remaining<=0)return new Result(0,true,null);
        if(remaining>1_000_000)return new Result(0,false,"post-event tick count outside projection budget");
        // An event may extend a use beyond its original duration. Never clamp that count.
        Input effective=new Input(in.duration(),remaining,in.damage(),in.maxDamage());
        return evaluateArguments(p,effective,List.of(Ref.STACK,Ref.PLAYER,remaining));
    }
    private static Result evaluateArguments(Program p,Input in,List<Object> args){
        Machine m=new Machine(p,in);
        try{run(m,p.root(),Ref.SELF,args,0);return new Result(m.swings,true,null);}catch(Boundary b){return new Result(m.swings,false,b.getMessage());}
    }
    private static Object pop(List<Object>s){if(s.isEmpty())throw new IllegalArgumentException("feedback stack underflow");return s.removeLast();}
    private static Number number(Object o){if(o instanceof Number n)return n;if(o==Ref.UNKNOWN)throw new Boundary("unknown source value controls client feedback");throw new IllegalArgumentException("feedback numeric receiver");}
    private static void expect(Object actual,Ref ref){if(actual!=ref)throw new IllegalArgumentException("feedback receiver "+ref);}
    private static Object run(Machine m,String key,Object receiver,List<Object> args,int depth){
        if(depth>=16)throw new IllegalArgumentException("feedback recursion budget");Code c=m.p.methods().get(key);if(c==null)throw new IllegalArgumentException("feedback helper missing");
        if(args.size()!=c.argumentSlots.length)throw new IllegalArgumentException("feedback helper arguments");
        Object[] l=new Object[c.locals()];Arrays.fill(l,Ref.UNKNOWN);if(!c.isStatic())l[0]=receiver;
        for(int i=0;i<args.size();i++)l[c.argumentSlots[i]]=args.get(i);List<Object>s=new ArrayList<>();int pc=0;
        while(pc>=0&&pc<c.steps.size()){
            if(--m.fuel<0||s.size()>128||m.swings>8)throw new IllegalArgumentException("feedback execution budget");Step i=c.steps.get(pc++);String op=i.op();
            switch(op){
                case "NOP"->{} case "NULL"->s.add(Ref.NULL);case "INT"->s.add(i.arg());case "FLOAT"->s.add(Float.parseFloat(i.text()));case "DOUBLE"->s.add(Double.parseDouble(i.text()));
                case "LOAD"->s.add(l[i.arg()]);case "STORE"->l[i.arg()]=pop(s);case "IINC"->l[i.arg()]=number(l[i.arg()]).intValue()+Integer.parseInt(i.text());
                case "POP"->pop(s);case "DUP"->{Object v=pop(s);s.add(v);s.add(v);}
                case "IADD","ISUB","IMUL","IDIV","IREM"->{int b=number(pop(s)).intValue(),a=number(pop(s)).intValue();s.add(switch(op){case "IADD"->a+b;case "ISUB"->a-b;case "IMUL"->a*b;case "IDIV"->a/b;default->a%b;});}
                case "FADD","FSUB","FMUL","FDIV","FREM"->{float b=number(pop(s)).floatValue(),a=number(pop(s)).floatValue();s.add(switch(op){case "FADD"->a+b;case "FSUB"->a-b;case "FMUL"->a*b;case "FDIV"->a/b;default->a%b;});}
                case "DADD","DSUB","DMUL","DDIV","DREM"->{double b=number(pop(s)).doubleValue(),a=number(pop(s)).doubleValue();s.add(switch(op){case "DADD"->a+b;case "DSUB"->a-b;case "DMUL"->a*b;case "DDIV"->a/b;default->a%b;});}
                case "INEG"->s.add(-number(pop(s)).intValue());case "FNEG"->s.add(-number(pop(s)).floatValue());case "DNEG"->s.add(-number(pop(s)).doubleValue());
                case "I2F","D2F"->s.add(number(pop(s)).floatValue());case "I2D","F2D"->s.add(number(pop(s)).doubleValue());case "F2I","D2I"->s.add(number(pop(s)).intValue());
                case "FCMPL","FCMPG","DCMPL","DCMPG"->{double b=number(pop(s)).doubleValue(),a=number(pop(s)).doubleValue();s.add(Double.isNaN(a)||Double.isNaN(b)?op.endsWith("L")?-1:1:a>b?1:a==b?0:-1);}
                case "GOTO"->pc=i.arg();
                case "IFEQ","IFNE","IFLT","IFLE","IFGT","IFGE","IF_ICMPEQ","IF_ICMPNE","IF_ICMPLT","IF_ICMPLE","IF_ICMPGT","IF_ICMPGE"->{int b=op.startsWith("IF_ICMP")?number(pop(s)).intValue():0,a=number(pop(s)).intValue();String suffix=op.startsWith("IF_ICMP")?op.substring(7):op.substring(2);boolean test=switch(suffix){case "EQ"->a==b;case "NE"->a!=b;case "LT"->a<b;case "LE"->a<=b;case "GT"->a>b;default->a>=b;};if(test)pc=i.arg();}
                case "IFNULL","IFNONNULL"->{Object x=pop(s);if(x==Ref.UNKNOWN)throw new Boundary("unknown nullable source value");if((x==Ref.NULL)==op.equals("IFNULL"))pc=i.arg();}
                case "WORLD"->{Object o=pop(s);if(o!=Ref.PLAYER&&o!=Ref.TARGET)throw new IllegalArgumentException("world receiver");s.add(Ref.WORLD);}
                case "REMOTE"->{expect(pop(s),Ref.WORLD);s.add(1);}
                case "DURATION"->{expect(pop(s),Ref.STACK);expect(pop(s),Ref.SELF);s.add(m.in.duration());}
                case "DAMAGE"->{expect(pop(s),Ref.STACK);if(m.serverResourceSeen)throw new Boundary("source resource mutation makes later damage read unknown");s.add(m.in.damage());}
                case "MAX"->{expect(pop(s),Ref.SELF);s.add(m.in.maxDamage());}
                case "EQUIPPED"->{number(pop(s));expect(pop(s),Ref.TARGET);s.add(Ref.UNKNOWN);}
                case "SWING"->{expect(pop(s),Ref.PLAYER);m.swings++;}
                case "BASE_USE_TICK"->{number(pop(s));expect(pop(s),Ref.PLAYER);expect(pop(s),Ref.STACK);expect(pop(s),Ref.SELF);}
                case "SERVER_RESOURCE"->{for(int n=0;n<i.arg();n++)pop(s);m.serverResourceSeen=true;}
                case "CALL"->{Code next=m.p.methods().get(i.text());List<Object> values=new ArrayList<>();for(int n=next.argumentSlots.length-1;n>=0;n--)values.addFirst(pop(s));Object recv=next.isStatic?null:pop(s);if(!next.isStatic&&recv!=Ref.SELF)throw new Boundary("non-self source helper receiver");Object result=run(m,i.text(),recv,values,depth+1);if(i.arg()!=0)s.add(result);}
                case "RETURN"->{if(!s.isEmpty())throw new IllegalArgumentException("feedback return stack");return null;}
                case "IRETURN","ARETURN"->{Object v=pop(s);if(!s.isEmpty())throw new IllegalArgumentException("feedback return stack");return v;}
                case "FRETURN","DRETURN"->{
                    Object v=pop(s);
                    if(v==Ref.UNKNOWN)throw new Boundary("unknown numeric helper return");
                    if(op.equals("FRETURN")?!(v instanceof Float):!(v instanceof Double))throw new IllegalArgumentException("feedback numeric return type");
                    if(!s.isEmpty())throw new IllegalArgumentException("feedback return stack");
                    return v;
                }
                case "DEFER"->throw new Boundary(i.text());
                default->throw new IllegalArgumentException("unknown feedback opcode "+op);
            }
        }throw new IllegalArgumentException("feedback missing terminator");
    }
}
