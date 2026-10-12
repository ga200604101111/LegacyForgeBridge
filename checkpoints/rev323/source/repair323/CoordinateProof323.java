
package dev.yinghuang.legacyforgebridge.repair322;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;import java.util.*;import static org.objectweb.asm.Opcodes.*;
/** Prevents coordinate-dependent geometry being flattened into a single positional sample. */
public final class CoordinateProof323 {
 static final class V extends BasicValue {final boolean coordinate;final Number constant;V(BasicValue v,boolean t,Number c){super(v==null?null:v.getType());coordinate=t;constant=c;}public boolean equals(Object o){return o instanceof V v&&super.equals(v)&&coordinate==v.coordinate&&Objects.equals(constant,v.constant);}public int hashCode(){return Objects.hash(super.hashCode(),coordinate,constant);}}
 final Map<String,ClassNode> classes;final Set<String> done=new HashSet<>(),active=new HashSet<>();
 CoordinateProof323(Map<String,ClassNode> classes){this.classes=classes;}
 static boolean taint(BasicValue v){return v instanceof V x&&x.coordinate;}
 static Number constant(BasicValue v){return v instanceof V x?x.constant:null;}
 static BasicValue wrap(BasicValue v,boolean t,Number c){return v==null?null:new V(v,t,c);}
 MethodNode find(String owner,String name,String desc){while(classes.containsKey(owner)){ClassNode c=classes.get(owner);for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;owner=c.superName;}return null;}
 void analyze(String owner,MethodNode method,Set<Integer> locals)throws AnalyzerException {
  String key=owner+"."+method.name+method.desc+locals;if(done.contains(key))return;if(!active.add(key))throw new IllegalArgumentException("recursive coordinate flow");
  BasicInterpreter interpreter=new BasicInterpreter(ASM9){
   public BasicValue newValue(Type t){return wrap(super.newValue(t),false,null);}
   public BasicValue newParameterValue(boolean instance,int local,Type type){return wrap(super.newValue(type),locals.contains(local),null);}
   public BasicValue newOperation(AbstractInsnNode n)throws AnalyzerException {Number c=null;int op=n.getOpcode();if(op>=ICONST_M1&&op<=ICONST_5)c=op-ICONST_0;else if(op>=FCONST_0&&op<=FCONST_2)c=(float)(op-FCONST_0);else if(op>=DCONST_0&&op<=DCONST_1)c=(double)(op-DCONST_0);else if(n instanceof IntInsnNode i&&(op==BIPUSH||op==SIPUSH))c=i.operand;else if(n instanceof LdcInsnNode l&&l.cst instanceof Number v)c=v;return wrap(super.newOperation(n),false,c);}
   public BasicValue copyOperation(AbstractInsnNode n,BasicValue v){return v;}
   public BasicValue unaryOperation(AbstractInsnNode n,BasicValue v)throws AnalyzerException {int op=n.getOpcode();boolean t=taint(v);if(t&&!Set.of(I2L,I2F,I2D,L2I,L2F,L2D,F2I,F2L,F2D,D2I,D2L,D2F).contains(op))throw new IllegalArgumentException("position-dependent unary geometry opcode="+op+" in "+method.name);return wrap(super.unaryOperation(n,v),t,t?null:constant(v));}
   public BasicValue binaryOperation(AbstractInsnNode n,BasicValue a,BasicValue b)throws AnalyzerException {boolean ta=taint(a),tb=taint(b);int op=n.getOpcode();if(ta||tb){if(!Set.of(IADD,ISUB,LADD,LSUB,FADD,FSUB,DADD,DSUB).contains(op)||ta&&tb||constant(ta?b:a)==null)throw new IllegalArgumentException("non-affine coordinate geometry opcode="+op+" in "+method.name);if(tb&&Set.of(ISUB,LSUB,FSUB,DSUB).contains(op))throw new IllegalArgumentException("negated coordinate geometry");}return wrap(super.binaryOperation(n,a,b),ta||tb,null);}
   public BasicValue ternaryOperation(AbstractInsnNode n,BasicValue a,BasicValue b,BasicValue c)throws AnalyzerException {if(taint(a)||taint(b)||taint(c))throw new IllegalArgumentException("coordinate stored in source array");return super.ternaryOperation(n,a,b,c);}
   public BasicValue naryOperation(AbstractInsnNode n,List<? extends BasicValue> values)throws AnalyzerException {boolean any=values.stream().anyMatch(CoordinateProof323::taint);if(any){if(!(n instanceof MethodInsnNode q))throw new IllegalArgumentException("coordinate dynamic call");MethodNode child=find(q.owner,q.name,q.desc);if(child!=null){Set<Integer> seeds=new TreeSet<>();int i=q.getOpcode()==INVOKESTATIC?0:1,local=i;for(Type type:Type.getArgumentTypes(q.desc)){if(taint(values.get(i++)))seeds.add(local);local+=type.getSize();}analyze(q.owner,child,seeds);}else{boolean world=(q.owner.equals("net/minecraft/world/IBlockAccess")||q.owner.equals("net/minecraft/world/World"))&&Set.of("func_72805_g","func_147439_a").contains(q.name);boolean block=q.owner.startsWith("net/minecraft/block/")&&Set.of("func_149646_a","func_149677_c","func_149720_d").contains(q.name);boolean render=q.owner.equals("net/minecraft/client/renderer/RenderBlocks")&&Set.of("func_147768_a","func_147806_b","func_147761_c","func_147734_d","func_147798_e","func_147764_f","func_147784_q","func_147793_a").contains(q.name);if(!world&&!block&&!render)throw new IllegalArgumentException("unproved coordinate consumer "+q.owner+"."+q.name);}}
    return wrap(super.naryOperation(n,values),false,null);}
   public void returnOperation(AbstractInsnNode n,BasicValue v,BasicValue expected)throws AnalyzerException {if(taint(v))throw new IllegalArgumentException("source returns coordinate value");super.returnOperation(n,v,expected);}
   public BasicValue merge(BasicValue a,BasicValue b){BasicValue base=Objects.equals(a.getType(),b.getType())?new BasicValue(a.getType()):super.merge(a,b);return wrap(base,taint(a)||taint(b),Objects.equals(constant(a),constant(b))?constant(a):null);}
  };
  try{new Analyzer<>(interpreter).analyze(owner,method);done.add(key);}finally{active.remove(key);}
 }
 public static void verify(Map<String,ClassNode> classes,String renderer)throws AnalyzerException {CoordinateProof323 proof=new CoordinateProof323(classes);MethodNode world=proof.find(renderer,"renderWorldBlock","(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;ILnet/minecraft/client/renderer/RenderBlocks;)Z");if(world==null)throw new IllegalArgumentException("renderer absent");proof.analyze(renderer,world,Set.of(2,3,4));}
}
