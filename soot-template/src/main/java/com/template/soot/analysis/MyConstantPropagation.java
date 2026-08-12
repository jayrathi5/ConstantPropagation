package com.template.soot.analysis;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.Stmt;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.Block;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MyConstantPropagation {
    private static final Object BOTTOM = new Object() {
        @Override
        public String toString() {
            return "BOTTOM";
        }
    };

    HashMap<Object,HashMap<Object,Object>>def=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>gen=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>out=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>in=new HashMap<>();
    HashMap<Object,HashMap<Object,Object>>kill=new HashMap<>();
    HashMap<Object,HashSet<Object>>blocktostatements=new HashMap<>();

    public MyConstantPropagation(SootMethod method) {
        doAnalysis(method);
    }

    @SuppressWarnings("unchecked")
    HashMap<Object,Object>setdiff(HashMap<Object,Object>in1,HashMap<Object,Object>kill){
        HashMap<Object,Object>res=new HashMap<>();
        for(Object i:in1.keySet()){
            if(!kill.containsKey(i)){
                res.put(i,in1.get(i));
            }
            else
            {
                Set<Object>killvalues=new HashSet<>((Collection<Object>) kill.get(i));
                Set<Object>in1values=new HashSet<>((Collection<Object>) in1.get(i));

                in1values.removeAll(killvalues);
                if(!in1values.isEmpty()){
                    res.put(i,in1values);
                }
            }

        }
        return res;
    }
    // Meet operator: absent key = TOP.
    //   TOP  meet value -> value
    //   value meet TOP  -> value
    //   v1 meet v1      -> v1        (same value on both sides)
    //   v1 meet v2      -> BOTTOM    (different values -> not constant)
    HashMap<Object,Object>union(HashMap<Object,Object>in1,HashMap<Object,Object>gen){
        HashMap<Object,Object>res=new HashMap<>();
        Set<Object>keys=new HashSet<>();
        keys.addAll(in1.keySet());
        keys.addAll(gen.keySet());
        for(Object key:keys){
            Object v1=in1.get(key);  // null == TOP
            Object v2=gen.get(key);  // null == TOP
            Object merged;
            if(v1==null){
                merged=v2;
            } else if(v2==null){
                merged=v1;
            } else if(v1.equals(v2)){
                merged=v1;
            } else {
                merged=BOTTOM;
            }
            res.put(key,merged);
        }
        return res;
    }
    void doAnalysis(SootMethod method) {
        Body body = method.retrieveActiveBody();
        BriefBlockGraph basicblock=new BriefBlockGraph(body);
        List<Block> blocks = basicblock.getBlocks();
        for(Block i:blocks){
            gen.put(i,new HashMap<>());
            for(Unit j:i.getUnits()){
                blocktostatements.put(i,new HashSet<>());
                blocktostatements.get(i).add(j);
                def.put(j,new HashMap<>());
                Stmt stmt = (Stmt) j;
                if(stmt instanceof AssignStmt){
                    AssignStmt assignStmt = (AssignStmt) stmt;
                    Value leftOp = assignStmt.getLeftOp();
                    Value rightOp = assignStmt.getRightOp();
                    if(rightOp instanceof Constant){
                        def.get(j).put(leftOp,rightOp);
                        gen.get(i).put(leftOp,rightOp);
                    }
            }

        }
    }
    //need to define kill
   // Kill(b)=(Def(v2)-gen(b))U(Def(v2)-gen(b))U...U(Def(vn)-gen(b))//
   for(Block b:blocks)
   {    HashMap<Object,Object>gen1=new HashMap<>(gen.get(b));
        for(Unit u:b.getUnits())
        {   
            for(Object v:gen1.keySet())
            {
                if(def.get(u).containsKey(v))
                {
                    HashMap<Object,Object>kill1=new HashMap<>(setdiff(def.get(u),gen.get(b)));
                    kill1.put(v,def.get(u).get(v));
                    if(kill.containsKey(b))
                    {
                        HashMap<Object,Object>kill2=new HashMap<>(kill.get(b));
                        kill2.putAll(kill1);
                        kill.put(b,kill2);
                    }
                    else
                    {
                        kill.put(b,kill1);
                    }
                }
                   
                }
            }
        }

    Queue<Block> worklist = new ArrayDeque<>();
    worklist.addAll(blocks);
    while(!worklist.isEmpty()){
        Block block=worklist.poll();
        HashMap<Object,Object>in1=new HashMap<>();
        List<Block> preds=block.getPreds();
        for(Block pred:preds){
            in1.putAll(out.get(pred));
        }
        HashMap<Object,Object>res=setdiff(in1,kill.get(block));
        res=union(res,gen.get(block));
        if(!res.equals(out.get(block))){
            out.put(block,res);
            List<Block> succs=block.getSuccs();
            worklist.addAll(succs);
        }
        
    }
}
}