package org.dllearner.utilities.neo4j;

import java.util.*;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

/**
 * Convert an OWL class expression to a Cypher query which returns all individuals
 * the class expression describes.
 *
 * @author Lorenz Buehmann
 */
public class OWLClassExpressionToCypherConverter extends OWLClassExpressionVisitorAdapter {
    private String query;
    private String inferenceQueryPart;

    private int cnt = 0;
    private int depth = 0;
    private Map<Integer, Map<OWLClass, String>> depthToNodeIDMapping = new HashMap<>();

    private Map<OWLClass, String> clsToNodeMapping = new HashMap<>();

    private Deque<String> currentNode = new ArrayDeque<>();
    private String targetNode;

    private boolean useInference = false;

    /**
     * Convert an OWL class expression to a Cypher query.
     * @param ce the class expression
     * @return the Cypher query
     */
    public String convert(OWLClassExpression ce) {
        return convert(ce, null);
    }

    /**
     * Convert an OWL class expression to a Cypher query.
     * @param ce the class expression
     * @param targetNode the target node name returned by the query
     * @return the Cypher query
     */
    public String convert(OWLClassExpression ce, String targetNode) {
        reset();

        query = "\nMATCH ";
        inferenceQueryPart = "";

        // if no target node name was given, create a new one
        if(targetNode == null) {
            targetNode = nextNode();
        }

        // put target node on stack
        currentNode.push(targetNode);

        // if inference is enabled, we have to process all classes in advance and assign nodes
//            if(useInference) {
//                precomputeInferences(ce);
//            }

        ce.accept(this);

        query = inferenceQueryPart + query + "\nRETURN " + targetNode;
        return query;
    }

    /**
     * Enable/disable light weight inference by means of stored procedures contained in the
     * Neosemantics plugin.
     * @param useInference whether to use inference or not
     */
    public void setUseInference(boolean useInference) {
        this.useInference = useInference;
    }

    private String nextNode() {
        return "n" + cnt++;
    }


    private String escapeLabel(OWLEntity entity) {
        return "`" + entity.toStringID() + "`";
    }

    private String escapeValue(OWLEntity entity) {
        return "'" + entity.toStringID() + "'";
    }

    private void reset() {
        query = "";
        inferenceQueryPart = "";
        targetNode = "";
        currentNode.clear();
        clsToNodeMapping.clear();
        depthToNodeIDMapping.clear();
        depth = 0;
        cnt = 0;
    }

    private void precomputeInferences(OWLClassExpression ce) {
        ce.getClassesInSignature().forEach(cls -> {
            // we need a new node
            String node = clsToNodeMapping.computeIfAbsent(cls, k -> nextNode());

            query += INF_CLASS_MEMBERS
                    .replace("%CLASS_URI%", cls.toStringID())
                    .replace("%TARGET_NODE%", node);
        });
    }

    @Override
    public void visit(OWLClass cls) {
        if (useInference) {
            String node = currentNode.peek();

            inferenceQueryPart += INF_CLASS_MEMBERS
                    .replace("%CLASS_URI%", cls.toStringID())
                    .replace("%TARGET_NODE%", node);

             query += "(" + node + ")";
        } else {
            query += "(" + currentNode.peek() + ":" + escapeLabel(cls) + ")";
        }
    }

    @Override
    public void visit(OWLObjectSomeValuesFrom ce) {
//           query += "MATCH ";
        query += "(" + currentNode.peek() + ")" + "-[:" + escapeLabel(ce.getProperty().asOWLObjectProperty()) + "]->";
        currentNode.push(nextNode());
        depth++;
        ce.getFiller().accept(this);
        depth--;
        currentNode.pop();
    }

    @Override
    public void visit(OWLObjectHasValue ce) {
        query += ",(" + currentNode.peek() + ")" +
                "-[:" + escapeLabel(ce.getProperty().asOWLObjectProperty()) + "]->" +
                "(" + nextNode() + " {uri: " + escapeValue(ce.getFiller().asOWLNamedIndividual()) + "})";
    }

    @Override
    public void visit(OWLDataSomeValuesFrom ce) {
        query += "MATCH (" + currentNode.peek() + ")\n" +
                "WHERE exists(" + currentNode.peek() + "." + escapeLabel(ce.getProperty().asOWLDataProperty()) + ")";
    }

    @Override
    public void visit(OWLDataHasValue ce) {
        query += "MATCH (" + currentNode.peek() + ")\n" +
                "WHERE " + currentNode.peek() + "." + escapeLabel(ce.getProperty().asOWLDataProperty()) +
                "=" + (ce.getFiller().getDatatype().isString()
                                            ? ("'" + ce.getFiller().getLiteral() + "'")
                                            : ce.getFiller().getLiteral());
    }

    final String INF_CLASS_MEMBERS = "CALL semantics.inference.nodesLabelled('%CLASS_URI%'," +
            "{ catLabel: \"Class\", subCatRel: \"SCO\", catNameProp: \"uri\" }) YIELD node AS %TARGET_NODE%\n";

    final String INF_CLASSES_MEMBERS = "WITH [%CLASSES%] as classes\n" +
            "UNWIND classes AS cls\n" +
            "CALL semantics.inference.nodesLabelled(cls,{ catLabel: \"Class\", subCatRel: \"SCO\", catNameProp: \"uri\" }) YIELD node AS n0\n" +
            "WITH cls, collect(n0) as nodesPerClass\n" +
            "WITH collect(nodesPerClass) as nodesPerClassList\n" +
            "WITH reduce(commonNodes = head(nodesPerClassList), otherNodes in tail(nodesPerClassList) |\n" +
            " apoc.coll.intersection(commonNodes, otherNodes)) as commonNodes\n" +
            "UNWIND commonNodes AS %TARGET_NODE%\n";
    private void processClassesInferred(List<OWLClass> classes) {
        if(classes.size() == 1) {
            inferenceQueryPart += INF_CLASS_MEMBERS
                    .replace("%CLASS_URI%", classes.get(0).toStringID())
                    .replace("%TARGET_NODE%", currentNode.peek());
        } else {
            inferenceQueryPart += INF_CLASSES_MEMBERS
                    .replace("%CLASSES%", classes.stream().map(this::escapeValue)
                                                                        .collect(Collectors.joining(", ")))
                    .replace("%TARGET_NODE%", currentNode.peek());
        }
    }
    @Override
    public void visit(OWLObjectIntersectionOf ce) {
        Set<OWLClassExpression> operands = ce.getOperands();

        // we process the classes first
        List<OWLClass> classes = operands.stream()
                .filter(op -> !op.isAnonymous())
                .map(OWLClassExpression::asOWLClass)
                .collect(Collectors.toList());

        query += "(" + currentNode.peek();
        if (useInference) {
            // we have to call some stored procedures here
            processClassesInferred(classes);
        } else {
            // without inference we simply append the labels
            query += classes.stream()
                        .map(this::escapeLabel)
                        .collect(Collectors.joining(":"));
        }
        query += ")";


        // process the complex class expressions
        List<OWLClassExpression> classExpressions = operands.stream().filter(OWLClassExpression::isAnonymous).collect(Collectors.toList());
        if (!classExpressions.isEmpty()) {
            query += ", ";
            classExpressions.get(0).accept(this);

            for (int i = 1; i < classExpressions.size(); i++) {
                query += ",";
                classExpressions.get(i).accept(this);
            }

        }

    }

    public static void main(String[] args) {
        ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());

        OWLClassExpressionToCypherConverter converter = new OWLClassExpressionToCypherConverter();
        converter.setUseInference(true);

        OWLDataFactory df = OWLManager.getOWLDataFactory();


        OWLClassExpression ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),
                df.getOWLObjectSomeValuesFrom(
                        df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/isCreatedBy")),
                        df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Customer"))
                )
        );
        System.out.println(ce + "\n" + converter.convert(ce));


        ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),
                df.getOWLObjectSomeValuesFrom(
                        df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/isCreatedBy")),
                        df.getOWLObjectSomeValuesFrom(
                                df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/isCreatedBy")),
                                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Customer"))
                        )
                )
        );
        System.out.println(ce + "\n" + converter.convert(ce));


        ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Requirement"))
        );
        System.out.println(ce + "\n" + converter.convert(ce));


        ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Requirement")),
                df.getOWLObjectSomeValuesFrom(
                        df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/isCreatedBy")),
                        df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Customer"))
                )
        );
        System.out.println(ce + "\n" + converter.convert(ce));


        ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),

                df.getOWLDataSomeValuesFrom(
                        df.getOWLDataProperty(IRI.create("http://www.w3.org/2000/01/rdf-schema#label")),
                        df.getTopDatatype()
                )
        );
        System.out.println(ce + "\n" + converter.convert(ce));


        ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),

                df.getOWLObjectHasValue(
                        df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/isCreatedBy")),
                        df.getOWLNamedIndividual(IRI.create("http://ns.softwiki.de/req/Derick_Garnier"))
                )
        );
        System.out.println(ce + "\n" + converter.convert(ce));


        ce = df.getOWLObjectIntersectionOf(
                df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),

                df.getOWLDataHasValue(
                        df.getOWLDataProperty(IRI.create("http://www.w3.org/2000/01/rdf-schema#label")),
                        df.getOWLLiteral("Use Database To Store User Data")
                )
        );
        System.out.println(ce + "\n" + converter.convert(ce));
    }
}