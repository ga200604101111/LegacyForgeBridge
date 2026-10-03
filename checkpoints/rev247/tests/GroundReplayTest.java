package dev.yinghuang.legacyforgebridge.behavior;
import java.nio.file.*;
public final class GroundReplayTest {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  Rev247GroundEvidence.decorate("CAPTURE_START","fixture",1,0);
  String d="pos=[0,7.5,0] ground=false incoming=[0,0.57,0] packetObject=1";
  String a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d,10,1);ok(!a.contains("postGroundPositiveCandidate"),"first apply not duplicate");
  Rev247GroundEvidence.decorate("GROUND_CONTACT","pos=[0,7.5,0] ground=true",30,1);
  a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d.replace("false","true").replace("=1","=2"),50,1);
  ok(a.contains("postGroundPositiveCandidate=true")&&a.contains("sourceJumpPositiveOrdinal=2"),"postground repeat found");
  ok(a.contains("diagnosticCausalProof=false"),"not server causation proof");
  a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d,60,2);ok(!a.contains("postGroundPositiveCandidate"),"new source jump not old duplicate");
  a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d.replace("0.57","-0.08"),70,2);ok(!a.contains("sourceJumpPositiveOrdinal"),"negative motion not classified positive");
  a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d.replace("0.57","NaN"),80,2);ok(!a.contains("sourceJumpPositiveOrdinal"),"nonfinite not classified");
  Rev247GroundEvidence.decorate("CAPTURE_START","fixture",100,0);
  a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d,110,1);ok(!a.contains("postGroundPositiveCandidate"),"capture reset");
  Rev247GroundEvidence.decorate("GROUND_CONTACT","pos=[0,7.5,0] ground=true",120,1);
  a=Rev247GroundEvidence.decorate("CLIENT_APPLY_HEAD",d,3_000_000_200L,1);ok(!a.contains("postGroundPositiveCandidate"),"bounded time window");
  int packets=0,repeats=0,grounded=0;StringBuilder found=new StringBuilder();
  for(String line:Files.readAllLines(Path.of(args[0]))){
   String[] p=line.split("\t",4);long ns=Long.parseLong(p[0]),jump=Long.parseLong(p[1]);
   String s=Rev247GroundEvidence.decorate(p[2],p[3],ns,jump);
   if(p[2].equals("CLIENT_APPLY_HEAD"))packets++;
   if(s.contains("repeatedPositiveForLocalJump=true"))repeats++;
   if(s.contains("postGroundPositiveCandidate=true")){grounded++;found.append("localJump=").append(jump).append(s).append('\n');}
  }
  ok(packets==89,"existing user trace apply count");ok(repeats==9,"all nine repeated positive observations");ok(grounded==5,"five previously unlabelled postground observations");
  Files.writeString(Path.of(args[1]),found.toString());
  System.out.println("PASS ground-evidence checks="+checks+" replayPackets="+packets+" repeated="+repeats+" postGround="+grounded+" mode=existing_trace_replay_not_new_game_test");
 }
}
