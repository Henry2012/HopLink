import cn.hqq.simplenode.NodeState;
public class NodeStateTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        NodeState s=new NodeState();s.nodes.add(new NodeState.Node("timeout","x",-1));s.nodes.add(new NodeState.Node("slow","x",500));s.nodes.add(new NodeState.Node("fast","x",20));s.nodes.add(new NodeState.Node("untested","x",0));
        check(s.sorted().get(0).name.equals("fast"));check(s.selected().equals("fast"));
        s.manual="slow";check(s.selected().equals("slow"));s.nodes.get(2).delay=1;check(s.selected().equals("slow"));
        s.manual=null;check(s.selected().equals("fast"));s.nodes.forEach(n->n.delay=-1);check(s.selected()==null);
        s.automaticHold="slow";check(s.selected().equals("slow"));s.manual="fast";check(s.selected().equals("fast"));s.manual=null;s.automaticHold=null;
        s.nodes.get(1).delay=30;s.nodes.get(2).delay=30;check(s.selected().equals("fast"));s.manual="missing";check(s.selected().equals("fast"));
        System.out.println("PASS: ordering, timeouts, fastest default, manual priority, missing selection and ties");
    }
}
