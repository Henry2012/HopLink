package cn.hqq.simplenode;

import java.util.*;

/** Selection policy is shared by the UI and offline tests. */
public final class NodeState {
    public static final class Node {
        public final String name, type;
        public int delay;
        public Node(String name,String type,int delay){this.name=name;this.type=type;this.delay=delay;}
    }
    public final List<Node> nodes = new ArrayList<>();
    public String manual;
    public String automaticHold;
    public List<Node> sorted(){
        List<Node> result=new ArrayList<>(nodes);
        result.sort(Comparator.comparingInt((Node n)->n.delay>0?n.delay:Integer.MAX_VALUE).thenComparing(n->n.name));
        return result;
    }
    public String selected(){
        if(manual!=null && nodes.stream().anyMatch(n->n.name.equals(manual)))return manual;
        if(automaticHold!=null && nodes.stream().anyMatch(n->n.name.equals(automaticHold)))return automaticHold;
        return sorted().stream().filter(n->n.delay>0).map(n->n.name).findFirst().orElse(null);
    }
}
