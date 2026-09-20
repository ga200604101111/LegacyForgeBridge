package dev.yinghuang.legacyforgebridge.compat;

import java.util.*;

/** Bounded geometric primitives shared by generated models, native shapes and mesh emission. */
public final class LegacyGeometry {
    private LegacyGeometry() { }
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public Box {
            double[] a={x0,y0,z0,x1,y1,z1};
            for(double v:a)if(!Double.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Box outside one block");
            if(x0>=x1||y0>=y1||z0>=z1)throw new IllegalArgumentException("Empty box");
        }
        public static Box from(List<Double> a){if(a==null||a.size()!=6)throw new IllegalArgumentException("Six coordinates required");return new Box(a.get(0),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5));}
        public List<Double> values(){return List.of(x0,y0,z0,x1,y1,z1);}
        boolean contains(double x,double y,double z){return x>x0&&x<x1&&y>y0&&y<y1&&z>z0&&z<z1;}
        public boolean full(){return equals(FULL);}
    }
    public static final Box FULL=new Box(0,0,0,1,1,1);
    /** Four vertices are counter-clockwise as seen from outside; UVs remain block-local. */
    public record Face(int side,List<Double> positions,List<Double> uv) {
        public Face {if(side<0||side>5||positions.size()!=12||uv.size()!=8)throw new IllegalArgumentException("Invalid quad");positions=List.copyOf(positions);uv=List.copyOf(uv);}
    }
    @FunctionalInterface public interface StairLookup {int at(int dx,int dz);}
    private static final int[] DX={1,-1,0,0},DZ={0,0,1,-1},HALVES={10,5,12,3};
    /** Source 1.7 stair metadata: east/west/south/north in the low bits; upper-half in bit 2. */
    public static int stairMask(int metadata,StairLookup neighbours) {
        validMeta(metadata);int dir=metadata&3,base=HALVES[dir];
        int front=neighbours.at(DX[dir],DZ[dir]);
        if(sameHalf(metadata,front)&&differentAxis(dir,front&3)) {
            int other=front&3;
            if(!sameFacing(metadata,neighbours.at(-DX[other],-DZ[other])))return base&HALVES[other];
        }
        int back=neighbours.at(-DX[dir],-DZ[dir]);
        if(sameHalf(metadata,back)&&differentAxis(dir,back&3)) {
            int other=back&3;
            if(!sameFacing(metadata,neighbours.at(DX[other],DZ[other])))return base|HALVES[other];
        }
        return base;
    }
    private static boolean differentAxis(int a,int b){return (a<2)!=(b<2);}
    private static boolean sameHalf(int a,int b){return b>=0&&b<=15&&(a&4)==(b&4);}
    private static boolean sameFacing(int a,int b){return sameHalf(a,b)&&(a&3)==(b&3);}
    public static List<Box> stairs(int metadata,int mask) {
        validMeta(metadata);if(mask<0||mask>15)throw new IllegalArgumentException("Invalid stair mask");
        boolean upper=(metadata&4)!=0;List<Box> out=new ArrayList<>();
        out.add(new Box(0,upper?.5:0,0,1,upper?1:.5,1));
        // Quadrants are kept separate for corner correctness; surface extraction drops internal faces.
        for(int i=0;i<4;i++)if((mask&(1<<i))!=0){double x=(i&1)*.5,z=(i>>>1)*.5;out.add(new Box(x,upper?0:.5,z,x+.5,upper?.5:1,z+.5));}
        return List.copyOf(out);
    }
    public static List<Box> stairs(int metadata){return stairs(metadata,HALVES[metadata&3]);}
    /** Bits: north, south, west, east. A legacy isolated pane is a full cross, not a post. */
    public static List<Box> panes(int connections) {
        if(connections<0||connections>15)throw new IllegalArgumentException("Invalid connection mask");
        int m=connections==0?15:connections;List<Box> out=new ArrayList<>();double lo=7d/16,hi=9d/16;
        out.add(new Box(lo,0,lo,hi,1,hi));
        if((m&1)!=0)out.add(new Box(lo,0,0,hi,1,lo));
        if((m&2)!=0)out.add(new Box(lo,0,hi,hi,1,1));
        if((m&4)!=0)out.add(new Box(0,0,lo,lo,1,hi));
        if((m&8)!=0)out.add(new Box(hi,0,lo,1,1,hi));
        return List.copyOf(out);
    }
    /** Use the same thin-panel semantics for baked fallback/held and native world models. */
    public static List<Face> paneFaces(int connections, boolean edges) {
        return edges ? surfaces(panes(connections)) : curtainFaces(connections);
    }
    public static List<Face> curtainFaces(int connections) {
        if(connections<0||connections>15)throw new IllegalArgumentException("Invalid connection mask");
        int m=connections==0?15:connections;List<Face> out=new ArrayList<>();
        if((m&1)!=0){out.add(face(4,.5,0,0,.5,1,.5));out.add(face(5,.5,0,0,.5,1,.5));}
        if((m&2)!=0){out.add(face(4,.5,0,.5,.5,1,1));out.add(face(5,.5,0,.5,.5,1,1));}
        if((m&4)!=0){out.add(face(2,0,0,.5,.5,1,.5));out.add(face(3,0,0,.5,.5,1,.5));}
        if((m&8)!=0){out.add(face(2,.5,0,.5,1,1,.5));out.add(face(3,.5,0,.5,1,1,.5));}
        return List.copyOf(out);
    }
    /** Finite grid union removes hidden faces and overlapping faces (especially stair corners). */
    public static List<Face> surfaces(List<Box> boxes) {
        if(boxes.isEmpty())return List.of();if(boxes.size()>16)throw new IllegalArgumentException("Geometry budget exceeded");
        SortedSet<Double> xs=new TreeSet<>(),ys=new TreeSet<>(),zs=new TreeSet<>();
        for(Box b:boxes){xs.add(b.x0);xs.add(b.x1);ys.add(b.y0);ys.add(b.y1);zs.add(b.z0);zs.add(b.z1);}
        Double[] x=xs.toArray(Double[]::new),y=ys.toArray(Double[]::new),z=zs.toArray(Double[]::new);
        boolean[][][] fill=new boolean[x.length-1][y.length-1][z.length-1];
        for(int i=0;i<x.length-1;i++)for(int j=0;j<y.length-1;j++)for(int k=0;k<z.length-1;k++){
            double cx=(x[i]+x[i+1])/2,cy=(y[j]+y[j+1])/2,cz=(z[k]+z[k+1])/2;
            for(Box b:boxes)if(b.contains(cx,cy,cz)){fill[i][j][k]=true;break;}
        }
        List<Face> out=new ArrayList<>();
        for(int i=0;i<x.length-1;i++)for(int j=0;j<y.length-1;j++)for(int k=0;k<z.length-1;k++)if(fill[i][j][k]){
            if(j==0||!fill[i][j-1][k])out.add(face(0,x[i],y[j],z[k],x[i+1],y[j],z[k+1]));
            if(j==y.length-2||!fill[i][j+1][k])out.add(face(1,x[i],y[j+1],z[k],x[i+1],y[j+1],z[k+1]));
            if(k==0||!fill[i][j][k-1])out.add(face(2,x[i],y[j],z[k],x[i+1],y[j+1],z[k]));
            if(k==z.length-2||!fill[i][j][k+1])out.add(face(3,x[i],y[j],z[k+1],x[i+1],y[j+1],z[k+1]));
            if(i==0||!fill[i-1][j][k])out.add(face(4,x[i],y[j],z[k],x[i],y[j+1],z[k+1]));
            if(i==x.length-2||!fill[i+1][j][k])out.add(face(5,x[i+1],y[j],z[k],x[i+1],y[j+1],z[k+1]));
        }
        return List.copyOf(out);
    }
    private static Face face(int side,double a,double b,double c,double d,double e,double f) {
        double[][] v=switch(side){
            case 0->new double[][]{{a,b,f},{a,b,c},{d,b,c},{d,b,f}};
            case 1->new double[][]{{a,e,c},{a,e,f},{d,e,f},{d,e,c}};
            case 2->new double[][]{{d,e,c},{d,b,c},{a,b,c},{a,e,c}};
            case 3->new double[][]{{a,e,f},{a,b,f},{d,b,f},{d,e,f}};
            case 4->new double[][]{{a,e,c},{a,b,c},{a,b,f},{a,e,f}};
            case 5->new double[][]{{d,e,f},{d,b,f},{d,b,c},{d,e,c}};
            default->throw new IllegalArgumentException("Invalid face");
        };
        List<Double> positions=new ArrayList<>(),uv=new ArrayList<>();
        for(double[] p:v){for(double coordinate:p)positions.add(coordinate);
            double u=switch(side){case 0,1,3->p[0];case 2->1-p[0];case 4->p[2];default->1-p[2];};
            double vv=switch(side){case 0->p[2];case 1->1-p[2];default->1-p[1];};uv.add(u);uv.add(vv);}
        return new Face(side,positions,uv);
    }
    private static void validMeta(int meta){if(meta<0||meta>15)throw new IllegalArgumentException("Invalid raw metadata");}
}
