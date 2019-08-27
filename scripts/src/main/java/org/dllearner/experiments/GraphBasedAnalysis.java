package org.dllearner.experiments;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.dllearner.utilities.graph.GraphUtils;
import org.dllearner.utilities.graph.OWLPropertyEdge;
import org.dllearner.utilities.graph.TypedOWLIndividual;
import org.jgrapht.Graph;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;

/**
 * @author Lorenz Buehmann
 */
public class GraphBasedAnalysis {

    private static Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>> getExamplesPoker(OWLOntology ont, String targetClass) {
        Set<TypedOWLIndividual> posExamples = ont.getIndividualsInSignature().stream()
                .filter(ind -> ont.getAnnotationAssertionAxioms(ind.asOWLNamedIndividual().getIRI()).stream()
                        .map(OWLAnnotationAssertionAxiom::annotationValue)
                        .map(OWLAnnotationValue::asLiteral)
                        .anyMatch(lit -> lit.isPresent() && lit.get().getLiteral().equals(targetClass)))
                .map(TypedOWLIndividual::new)
                .collect(Collectors.toSet());

        OWLClass hand = new OWLClassImpl(IRI.create("http://dl-learner.org/examples/uci/poker#Hand"));
        Set<TypedOWLIndividual> negExamples = ont.getABoxAxioms(Imports.INCLUDED).stream()
                .filter(ax -> ax.isOfType(AxiomType.CLASS_ASSERTION))
                .map(ax -> (OWLClassAssertionAxiom)ax)
                .filter(ax -> ax.getClassExpression().equals(hand))
                .map(OWLClassAssertionAxiom::getIndividual)
                .filter(ind -> ont.getAnnotationAssertionAxioms(ind.asOWLNamedIndividual().getIRI()).stream()
                        .map(OWLAnnotationAssertionAxiom::annotationValue)
                        .map(OWLAnnotationValue::asLiteral)
                        .anyMatch(lit -> lit.isPresent() && !lit.get().getLiteral().equals(targetClass)))
                .map(TypedOWLIndividual::new)
                .collect(Collectors.toSet());

        return Pair.of(posExamples, negExamples);
    }

    private static Pair<Graph<TypedOWLIndividual, OWLPropertyEdge>,
            Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>>> pokerDataset() throws OWLOntologyCreationException {
        OWLOntology ont = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
                new File("/home/user/work/datasets/poker/poker_straight_flush_p5-n347.owl"));
        final String targetClass = "straight_flush";

        Graph<TypedOWLIndividual, OWLPropertyEdge> g = GraphUtils.aboxToLabeledGraphWithTypes(ont);

        Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>> startNodes = getExamplesPoker(ont, targetClass);

        return Pair.of(g, startNodes);
    }

    private static Pair<Graph<TypedOWLIndividual, OWLPropertyEdge>,
            Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>>> sworeDataset() throws OWLOntologyCreationException {
        OWLOntology ont = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
                new File("../examples/swore/swore.rdf"));

        Graph<TypedOWLIndividual, OWLPropertyEdge> g = GraphUtils.aboxToLabeledGraphWithTypes(ont);
        final OWLClass targetClass = new OWLClassImpl(IRI.create("http://ns.softwiki.de/req/CustomerRequirement"));
        Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>> startNodes = getExamplesSwore(ont, targetClass);

        return Pair.of(g, startNodes);
    }

    private static Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>>  getExamplesSwore(OWLOntology ont, OWLClass targetClass) {
        Set<TypedOWLIndividual> posExamples =  ont.getClassAssertionAxioms(targetClass).stream()
                .map(OWLClassAssertionAxiom::getIndividual)
                .map(TypedOWLIndividual::new)
                .collect(Collectors.toSet());

        Set<TypedOWLIndividual> negExamples = Sets.difference(ont.getIndividualsInSignature(),
                                                        posExamples.stream().map(TypedOWLIndividual::getIndividual).collect(Collectors.toSet()))
                .stream()
                .map(TypedOWLIndividual::new)
                .collect(Collectors.toSet());

        return Pair.of(posExamples, negExamples);
    }

    public static void main(String[] args) throws Exception {
        ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());

        int maxPathLength = 10;

        Pair<Graph<TypedOWLIndividual, OWLPropertyEdge>, Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>>> dataset;
//        dataset = pokerDataset();
        dataset = sworeDataset();

        Graph<TypedOWLIndividual, OWLPropertyEdge> g = dataset.getLeft();
        Pair<Set<TypedOWLIndividual>, Set<TypedOWLIndividual>> startNodes = dataset.getRight();

//        startNodes.forEach(node -> {
//
//            System.out.println("----------------------------------------------");
//            System.out.println("node: " + node);
//
//            // compute all path up to length
//            List<GraphPath<TypedOWLIndividual, OWLPropertyEdge>> paths = new AllPaths<>(g).getAllPaths(node, true, maxPathLength);
//
//            // show all paths
//            paths.forEach(System.out::println);
//
//            // show all paths but just the edges
//            List<List<OWLObjectPropertyExpression>> pathEdges = paths.stream()
//                    .map(path -> path.getEdgeList().stream().map(LabeledEdge::getLabel).collect(Collectors.toList()))
//                    .collect(Collectors.toList());
////            pathEdges.forEach(System.out::println);
//
//            // compute frequency per edge sequence
//            Map<List<OWLObjectPropertyExpression>, Long> edgeSequenceWithFrequency = pathEdges.stream()
//                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
//
//            // sort by length
//            Comparator<List<OWLObjectPropertyExpression>> c = Comparator
//                    .<List<OWLObjectPropertyExpression>>comparingInt(List::size)
//                    .thenComparing(Object::toString);
//            SortedMap<List<OWLObjectPropertyExpression>, Long> edgeSequenceWithFrequencySorted = new TreeMap<>(c);
//            edgeSequenceWithFrequencySorted.putAll(edgeSequenceWithFrequency);
//
//            edgeSequenceWithFrequencySorted.forEach((k, v) -> System.out.println(v + "\t:" + k));
//
//        });

        Lists.newArrayList(startNodes.getLeft(), startNodes.getRight()).forEach(nodes -> {

                    Map<TypedOWLIndividual, TreeMap<List<OWLObjectPropertyExpression>, Long>> nodeToPathsWithFrequencies =
                            GraphUtils.getPathsWithFrequencies(g, nodes, maxPathLength);

//        nodeToPathsWithFrequencies.forEach((node, paths) -> {
//            System.out.println("node " + node);
//            paths.forEach((edges, frequency) -> System.out.println(frequency + "\t:" + edges));
//        });

                    // group by examples
                    Comparator<List<OWLObjectPropertyExpression>> c = Comparator
                            .<List<OWLObjectPropertyExpression>>comparingInt(List::size)
                            .thenComparing(Object::toString);
                    Map<List<OWLObjectPropertyExpression>, Long> pathWithFrequency = nodeToPathsWithFrequencies.entrySet().stream()
                            .flatMap(e -> e.getValue().keySet().stream())
                            .collect(Collectors.groupingBy(Function.identity(), () -> new TreeMap<>(c), Collectors.counting()));

                    pathWithFrequency.forEach((edges, frequency) -> System.out.println(frequency + "\t:" + edges));
                }
        );
    }
}
