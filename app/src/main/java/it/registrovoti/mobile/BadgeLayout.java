package it.registrovoti.mobile;

import java.util.ArrayList;
import java.util.List;

final class BadgeLayout {
    static final class Row {
        int l,r,t,b, start,end,top,bottom;
        Row(int l,int r,int y){this.l=l;this.r=r;t=b=y;}
        boolean similar(Row row,int w){return Math.abs(l-row.l)<w*.018 && Math.abs(r-row.r)<w*.027;}
    }
    static List<Row> detect(int[] pixels,int w,int h){
        List<Row> bands=new ArrayList<>();
        Row band=null;
        int end=(int)(w*(w>h*1.4?.08:.26));
        for(int y=0;y<=h;y++){
            int left=end,right=0,count=0;
            if(y<h) for(int x=0;x<end;x++){
                int c=pixels[y*w+x],r=c>>16&255,g=c>>8&255,b=c&255;
                int max=Math.max(r,Math.max(g,b));
                if(max>60 && max-Math.min(r,Math.min(g,b))>45){left=Math.min(left,x);right=x;count++;}
            }
            int width=right-left+1;
            if(width>=Math.max(12,w*.021) && width<w*.23 && count>width*.52){
                if(band==null) band=new Row(left,right,y);
                band.l=Math.min(band.l,left);band.r=Math.max(band.r,right);band.b=y;
            }else if(band!=null){
                int height=band.b-band.t+1;
                double ratio=(band.r-band.l+1)/(double)height;
                if(height>=8 && ratio>1.25 && ratio<4.5)bands.add(band);
                band=null;
            }
        }
        Row best=null;int highest=0;
        for(Row s:bands){
            int hits=0;for(Row row:bands)if(s.similar(row,w))hits++;
            if(hits>highest){highest=hits;best=s;}
        }
        List<Row> result=new ArrayList<>();
        for(Row row:bands){
            if(!row.similar(best,w))continue;
            int width=row.r-row.l,x=Math.max(0,row.l-Math.max(4,(int)Math.round(width*.13)));
            row.top=row.bottom=(row.t+row.b)/2;
            int base=pixels[row.top*w+x];
            while(row.top>0 && same(pixels[(row.top-1)*w+x],base))row.top--;
            while(row.bottom+1<h && same(pixels[(row.bottom+1)*w+x],base))row.bottom++;
            boolean mobile=width>w*.09;
            row.start=row.r+(int)Math.round(width*(mobile?.08:1.5));row.end=(int)(w*(mobile?.89:.49));
            result.add(row);
        }
        return result;
    }
    private static boolean same(int a,int b){
        for(int shift=0;shift<=16;shift+=8)if(Math.abs(((a>>shift)&255)-((b>>shift)&255))>=8)return false;
        return true;
    }
    private static boolean ink(int c){return (c&255)>150 && ((c>>8)&255)>150 && ((c>>16)&255)>150;}
    static final class Glyph {
        final String raw,bits;final double ratio;
        Glyph(String raw,String bits,double ratio){this.raw=raw;this.bits=bits;this.ratio=ratio;}
    }
    static String match(int[] pix,int w,Row row,List<Glyph> samples){
        int l=row.r+1,r=row.l,t=row.b+1,b=row.t;
        for(int y=row.t;y<=row.b;y++)for(int x=row.l;x<=row.r;x++)if(ink(pix[y*w+x])){
            l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
        }
        if(l>r || t>b)return null;
        int width=r-l+1,height=b-t+1;
        StringBuilder mask=new StringBuilder();
        for(int i=0;i<384;i++){
            int x=l+(int)((i%24+.5)*width/24),y=t+(int)((i/24+.5)*height/16);
            mask.append(ink(pix[y*w+x])?'1':'0');
        }
        java.util.Map<String,Double> scores=new java.util.HashMap<>();
        for(Glyph sample:samples){
            int diff=0;for(int i=0;i<384;i++)if(mask.charAt(i)!=sample.bits.charAt(i))diff++;
            double score=diff/384.0+Math.abs(Math.log(width/(double)height/sample.ratio))*.28;
            scores.merge(sample.raw,score,Math::min);
        }
        double best=1,second=1;String raw=null;
        for(java.util.Map.Entry<String,Double> e:scores.entrySet()){
            double s=e.getValue();
            if(s<best){second=best;best=s;raw=e.getKey();}else second=Math.min(second,s);
        }
        return best<.12 && second-best>.045?raw:null;
    }
}
