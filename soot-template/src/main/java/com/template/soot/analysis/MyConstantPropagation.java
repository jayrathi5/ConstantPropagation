package com.template.soot.analysis;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.Constant;
import soot.jimple.IntConstant;
import soot.jimple.RemExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.Block;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class MyConstantPropagation {
    HashMap<Object,HashMap<Object,Object>>def=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>gen=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>out=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>in=new HashMap<>();
    // HashMap<Object,HashMap<Object,Object>>kill=new HashMap<>();
    HashMap<Object,HashSet<Object>>blocktostatements=new HashMap<>();

    public MyConstantPropagation(SootMethod method) {
        doAnalysis(method);
    }
    static final Object TOP = new Object();
static final Object BOT = new Object();

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

                    if (lv == BOT || rv == BOT) return BOT;                          // ⊥ dominates
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
    void doAnaylsis(SootMethod method)
    {
        BriefBlockGraph blockgraph=new BriefBlockGraph(method.getActiveBody());
        List<Block> blocks=blockgraph.getBlocks();
        for(Block b:blocks)
        {
            in.put(b,new HashMap<>());
            out.put(b,new HashMap<>());
            def.put(b,new HashMap<>());
            for(Unit u:b.getUnits())
            {   
                Statement s=(Statement)u;
                if(s instanceof AssignStmt)
                {
                    Value leftop=s.getLeftOp();
                    Value rightop=s.getRightOp();
                    if(leftop instanceof Local)
                    {
                       in.get(b).put(leftop,'T');
                       out.get(b).put(leftop,'T');
                        if(rightop instanceof Constant)
                        {
                            def.get(b).put(leftop,rightop);
                        }
                        else if(rightop instanceof Local)
                        {
                            def.get(b).put(leftop,def.get(b).get(rightop));
                        }
                        else if(rightop instanceof BinopExpr)
                        {
                            def.get(b).put(leftop,evaluate(rightop));
                        }
                    }
                    if(rightop instanceof Local)
                    {in.get(b).put(rightop,'T');
                    out.get(b).put(rightop,'T');
                        
                    }
                    
                    
                }
            }


        }
        Queue<Block> worklist=new LinkedList<>(blocks);
        while(!worklist.isEmpty())
        {
            Block b=worklist.poll();
            
            HashMap<Object,Object>def=new HashMap<>(this.def.get(b));
            HashMap<Object,Object>in1=new HashMap<>();
            HashMap<Object,Object>out1=new HashMap<>();
            for(Block b1:b.getPreds())
            {   
                in.put(b,meet(in1.get(b1),this.out.get(b1)));

            }
            out.put(b,meet(in.get(b),def.get(b)));
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