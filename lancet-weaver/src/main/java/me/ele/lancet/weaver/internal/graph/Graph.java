package me.ele.lancet.weaver.internal.graph;

import me.ele.lancet.base.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by gengwanpeng on 17/5/5.
 */
public class Graph {


    private final Map<String, Node> nodeMap;

    public Graph(Map<String, Node> nodesMap) {
        this.nodeMap = nodesMap;
    }

    public void prepare() {
        nodeMap.values()
                .forEach(n -> {
                    if (n.parent != null) {
                        me.ele.lancet.weaver.internal.graph.ClassNode parent = n.parent;
                        if (parent.children == Collections.EMPTY_LIST) {
                            if (parent.entity.name.equals("java/lang/Object")) {
                                parent.children = new ArrayList<>(nodeMap.size() >> 1);
                            } else {
                                parent.children = new ArrayList<>();
                            }
                        }
                        // all interfaces extends java.lang.Object
                        // make java.lang.Object subclasses purely
                        if (n instanceof ClassNode) {
                            parent.children.add((ClassNode) n);
                        }
                    }
                    n.interfaces.forEach(i -> {
                        if (n instanceof InterfaceNode) {
                            if (i.children == Collections.EMPTY_LIST) {
                                i.children = new ArrayList<>();
                            }
                            i.children.add((InterfaceNode) n);
                        } else {
                            if (i.implementedClasses == Collections.EMPTY_LIST) {
                                i.implementedClasses = new ArrayList<>();
                            }
                            //noinspection ConstantConditions
                            i.implementedClasses.add((me.ele.lancet.weaver.internal.graph.ClassNode) n);
                        }
                    });
                });
    }

    public boolean inherit(String child, String parent) {
        Node node = nodeMap.get(child);
        while (node != null && !parent.equals(node.entity.name)) {
            node = node.parent;
        }
        return node != null;
    }

    /**
     * assert class always in nodeMap, if not, it's our code error.
     */
    public NodeVisitor childrenOf(String className, Scope scope) {
        return visitor -> {
            Node node = nodeMap.get(className);
            if (!(node instanceof ClassNode)) {
                throw new IllegalArgumentException(className + " is not a class");
            }
            visitClasses((ClassNode) node, scope, visitor);
        };
    }

    private void visitClasses(ClassNode parent, Scope scope, Consumer<Node> visitor) {
        List<ClassNode> children = parent.children;
        switch (scope) {
            case SELF:
                visitor.accept(parent);
                break;
            case ALL:
                children.forEach(n -> visitClasses(n, scope, visitor));
            case DIRECT:
                children.forEach(visitor);
                break;
            case LEAF:
                children.stream()
                        .filter(n -> {
                            if (n.children.size() == 0) {
                                visitor.accept(n);
                                return false;
                            }
                            return true;
                        })
                        .forEach(n -> visitClasses(n, scope, visitor));
                break;
        }
    }

    public NodeVisitor implementsOf(String interfaceName, Scope scope) {
        return visitor -> {
            Node node = nodeMap.get(interfaceName);
            if (!(node instanceof InterfaceNode)) {
                throw new IllegalArgumentException(interfaceName + " is not a interface");
            }
            visitImplements((InterfaceNode) node, scope, visitor);
        };
    }

    private void visitImplements(InterfaceNode node, Scope scope, Consumer<Node> visitor) {
        List<ClassNode> classes = node.implementedClasses;
        List<InterfaceNode> children = node.children;
        switch (scope) {
            case ALL:
                classes.forEach(c -> visitClasses(c, scope, visitor));
            case DIRECT:
                children.forEach(c -> visitImplements(c, scope, visitor));
            case SELF:
                classes.forEach(visitor);
                break;
            case LEAF:
                children.forEach(c -> visitImplements(c, scope, visitor));
                classes.stream()
                        .filter(c -> {
                            if (c.children.size() <= 0) {
                                visitor.accept(c);
                                return false;
                            }
                            return true;
                        })
                        .forEach(c -> visitClasses(c, scope, visitor));
        }
    }

    public Node get(String className) {
        return nodeMap.get(className);
    }


    public interface NodeVisitor {
        void forEach(Consumer<Node> node);
    }
}
