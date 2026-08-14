package com.template.soot.analysis;

import soot.Body;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AddExpr;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.Constant;
import soot.jimple.DivExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IntConstant;
import soot.jimple.MulExpr;
import soot.jimple.RemExpr;
import soot.jimple.Stmt;
import soot.jimple.SubExpr;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MyConstantPropagation {
    HashMap<Object,HashMap<Object,Object>>def=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>gen=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>out=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>in=new HashMap<>();
    HashMap<Object,HashSet<Object>>blocktostatements=new HashMap<>();

    static final Object TOP = new Object();
    static final Object BOT = new Object();
    private final Set<Local> allLocals = new HashSet<>();

    private List<Block> blocks;

    public MyConstantPropagation(SootMethod method) {
        doAnalysis(method);
    }

    public HashMap<Object,Object> getIn(Block b)  { return in.get(b); }
    public HashMap<Object,Object> getOut(Block b) { return out.get(b); }

    private static String fmt(Object v) {
        if (v == TOP) return "TOP";
        if (v == BOT) return "BOT";
        return String.valueOf(v);
    }

    public void printResults() {
        for (Block b : blocks) {
            System.out.println("Block " + b.getIndexInMethod() + " " + b.getHead() + " .. " + b.getTail());
            System.out.println("  IN:  " + fmtMap(in.get(b)));
            for (Unit u : b) System.out.println("    " + u);
            System.out.println("  OUT: " + fmtMap(out.get(b)));
        }
    }

    private String fmtMap(HashMap<Object,Object> m) {
        StringBuilder sb = new StringBuilder("{");
        for (Local l : allLocals) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(l).append("=").append(fmt(m.get(l)));
        }
        return sb.append("}").toString();
    }
    Object evaluate(Value rhs, Block b) {
        if (!(rhs instanceof BinopExpr)) return BOT;
        Value leftop  = ((BinopExpr) rhs).getOp1();
        Value rightop = ((BinopExpr) rhs).getOp2();

        if (leftop instanceof Constant && rightop instanceof Constant) {
            if (!(leftop instanceof IntConstant) || !(rightop instanceof IntConstant)) return BOT;
            int l = ((IntConstant) leftop).value;
            int r = ((IntConstant) rightop).value;

            if (rhs instanceof AddExpr) return l + r;
            if (rhs instanceof SubExpr) return l - r;
            if (rhs instanceof MulExpr) return l * r;
            if (rhs instanceof DivExpr) return r == 0 ? BOT : l / r;
            if (rhs instanceof RemExpr) return r == 0 ? BOT : l % r;
            return BOT;
        }
        else if (leftop instanceof Constant && rightop instanceof Local) {
            if (!(leftop instanceof IntConstant)) return BOT;
            Object rv = def.get(b).get(rightop);
            if (rv == BOT) return BOT;
            if (rv == null || rv == TOP) return TOP;
            if (!(rv instanceof Integer)) return BOT;

            int l = ((IntConstant) leftop).value;
            int r = (Integer) rv;

            if (rhs instanceof AddExpr) return l + r;
            if (rhs instanceof SubExpr) return l - r;
            if (rhs instanceof MulExpr) return l * r;
            if (rhs instanceof DivExpr) return r == 0 ? BOT : l / r;
            if (rhs instanceof RemExpr) return r == 0 ? BOT : l % r;
            return BOT;
        }
        else if (leftop instanceof Local && rightop instanceof Constant) {
            if (!(rightop instanceof IntConstant)) return BOT;
            Object lv = def.get(b).get(leftop);
            if (lv == BOT) return BOT;
            if (lv == null || lv == TOP) return TOP;
            if (!(lv instanceof Integer)) return BOT;

            int l = (Integer) lv;
            int r = ((IntConstant) rightop).value;

            if (rhs instanceof AddExpr) return l + r;
            if (rhs instanceof SubExpr) return l - r;
            if (rhs instanceof MulExpr) return l * r;
            if (rhs instanceof DivExpr) return r == 0 ? BOT : l / r;
            if (rhs instanceof RemExpr) return r == 0 ? BOT : l % r;
            return BOT;
        }
        else if (leftop instanceof Local && rightop instanceof Local) {
            Object lv = def.get(b).get(leftop);
            Object rv = def.get(b).get(rightop);

            if (lv == BOT || rv == BOT) return BOT;                          // bottom dominates
            if (lv == null || lv == TOP || rv == null || rv == TOP) return TOP;
            if (!(lv instanceof Integer) || !(rv instanceof Integer)) return BOT;

            int l = (Integer) lv;
            int r = (Integer) rv;

            if (rhs instanceof AddExpr) return l + r;
            if (rhs instanceof SubExpr) return l - r;
            if (rhs instanceof MulExpr) return l * r;
            if (rhs instanceof DivExpr) return r == 0 ? BOT : l / r;
            if (rhs instanceof RemExpr) return r == 0 ? BOT : l % r;
            return BOT;
        }

        return BOT;
    }

    HashMap<Object,Object> meet(HashMap<Object,Object> in1,HashMap<Object,Object> in2)
    {
        HashMap<Object,Object> result=new HashMap<>();
        Set<Object> keys=new HashSet<>();
        keys.addAll(in1.keySet());
        keys.addAll(in2.keySet());
        for(Object key:keys)
        {
            Object v1=in1.get(key);
            Object v2=in2.get(key);
            if(v1==null)v1=TOP;
            if(v2==null)v2=TOP;
            if(v1.equals(v2))
            {
                result.put(key,v1);
            }
            else if(v1.equals(TOP))
            {
                result.put(key,v2);
            }
            else if(v2.equals(TOP))
            {
                result.put(key,v1);
            }
            else
            {
                result.put(key,BOT);
            }
        }
        return result;
    }

    void doAnalysis(SootMethod method)
    {
        Body body=method.getActiveBody();
        BriefBlockGraph blockgraph=new BriefBlockGraph(body);
        List<Block> blocks=blockgraph.getBlocks();
        this.blocks=blocks;
        allLocals.addAll(body.getLocals());

    
        for(Block b:blocks)
        {
            HashMap<Object,Object> topmap=new HashMap<>();
            for(Local l:allLocals)topmap.put(l,TOP);
            in.put(b,new HashMap<>(topmap));
            out.put(b,new HashMap<>(topmap));
            def.put(b,new HashMap<>(topmap));
        }

        Queue<Block> worklist=new LinkedList<>(blocks);
        while(!worklist.isEmpty())
        {
            Block b=worklist.poll();

            HashMap<Object,Object>out1=new HashMap<>(out.get(b));
            HashMap<Object,Object>in1=new HashMap<>();

            for(Block b1:b.getPreds())
            {   if(in1.isEmpty())
                {
                    in1.putAll(this.out.get(b1));
                }
                else
                {
                    in1=meet(in1,this.out.get(b1));
                }
            }
            if(b.getPreds().isEmpty())       
            {
                for(Local l:allLocals)in1.put(l,TOP);
            }
            in.put(b,in1);

    
            HashMap<Object,Object>def1=new HashMap<>(in1);
            def.put(b,def1);

            for(Unit u:b)
            {
                Stmt s=(Stmt)u;

                if(s instanceof IdentityStmt)
                {
                    Value leftop=((IdentityStmt)s).getLeftOp();
                    if(leftop instanceof Local)def1.put(leftop,BOT);//just to handle this keyword and any other because it is allocated dynamically and we don't know its value at compile time.
                    continue;
                }
                if(!(s instanceof AssignStmt))continue;

                Value leftop=((AssignStmt)s).getLeftOp();
                Value rightop=((AssignStmt)s).getRightOp();
                if(!(leftop instanceof Local))continue;   

                if(rightop instanceof IntConstant)
                {
                    def1.put(leftop,((IntConstant)rightop).value);
                }
                else if(rightop instanceof Constant)     
                {
                    def1.put(leftop,BOT);
                }
                else if(rightop instanceof Local)
                {
                    Object rv=def1.get(rightop);
                    def1.put(leftop,rv==null?TOP:rv);
                }
                else if(rightop instanceof BinopExpr)
                {
                    def1.put(leftop,evaluate(rightop,b));
                }
                else                                      
                {
                    def1.put(leftop,BOT);
                }
            }

            out.put(b,new HashMap<>(in1));
            for(Object v:def1.keySet())
            {
                out.get(b).put(v,def1.get(v));
            }
            if(!out.get(b).equals(out1))
            {
                for(Block b2:b.getSuccs())
                {
                    worklist.add(b2);
                }
            }
        }
    }
}