package org.dllearner.utilities.graph;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.dllearner.utilities.examples.ExamplesProvider;
import org.glassfish.json.JsonUtil;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

/**
 * Utility methods working on graph level by means of JGraphT API.
 *
 * @author Lorenz Buehmann
 */
public class GraphUtils {

    /**
     * Maps the ABox of an OWL ontology to a directed graph.
     * <p>
     * It just uses object property assertions to build the graph of the individuals. Edges are not labeled.
     *
     * @param ont the ontology
     * @return a directed graph with labeled vertices
     */
    public static Graph<OWLIndividual, DefaultEdge> aboxToGraph(OWLOntology ont) {

        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);

        Graph<OWLIndividual, DefaultEdge> g = GraphTypeBuilder
                .directed()
                .allowingMultipleEdges(true)
                .edgeClass(DefaultEdge.class)
                .vertexClass(OWLIndividual.class)
                .buildGraph();

        axioms.forEach(ax -> g.addEdge(ax.getSubject(), ax.getObject()));

        return g;
    }

    /**
     * Maps the ABox of an OWL ontology to a labeled directed graph.
     * <p>
     * It just uses object property assertions to build the graph of the individuals. Edges are labeled with the
     * object property.
     *
     * @param ont the ontology
     * @return a directed graph with labeled vertices and edges
     */
    public static Graph<OWLIndividual, OWLPropertyEdge> aboxToLabeledGraph(OWLOntology ont) {

        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);

        Graph<OWLIndividual, OWLPropertyEdge> g = GraphTypeBuilder
                .directed()
                .allowingMultipleEdges(true)
                .vertexClass(OWLIndividual.class)
                .edgeClass(OWLPropertyEdge.class)
                .buildGraph();

        axioms.forEach(ax -> {
            g.addVertex(ax.getSubject());
            g.addVertex(ax.getObject());
            g.addEdge(ax.getSubject(), ax.getObject(), new OWLPropertyEdge(ax.getProperty()));
        });

        return g;
    }

    private static TypedOWLIndividual toTypedIndividual(OWLIndividual ind, OWLOntology ont) {
        Set<OWLClass> types = ont.getClassAssertionAxioms(ind).stream()
                .map(OWLClassAssertionAxiom::getClassExpression)
                .filter(ce -> !ce.isAnonymous() && !ce.isOWLThing())
                .map(OWLClassExpression::asOWLClass)
                .collect(Collectors.toSet());

        return new TypedOWLIndividual(ind, types);
    }

    /**
     * Maps the ABox of an OWL ontology to a labeled directed graph.
     * <p>
     * It just uses object property assertions to build the graph of the individuals.
     * Edges are labeled with the object property.
     * Vertices are annotated with the types of the individuals.
     *
     * @param ont the ontology
     * @return a directed graph with labeled and type annotated vertices as well as labeled edges
     */
    public static Graph<TypedOWLIndividual, OWLPropertyEdge> aboxToLabeledGraphWithTypes(OWLOntology ont) {

        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);

        Graph<TypedOWLIndividual, OWLPropertyEdge> g = GraphTypeBuilder
                .directed()
                .allowingMultipleEdges(true)
                .vertexClass(TypedOWLIndividual.class)
                .edgeClass(OWLPropertyEdge.class)
                .buildGraph();

        // we cache the created vertices because computation of types can be expensive,
        // at least when inference would be used
        Map<OWLIndividual, TypedOWLIndividual> cache = new HashMap<>();

        // we also have to add all individuals not occurring in any property relation, so
        // we just create nodes for all individuals and add them to the graph
        ont.getIndividualsInSignature().forEach(ind -> g.addVertex(cache.computeIfAbsent(ind, i -> toTypedIndividual(i, ont))));

        axioms.forEach(ax -> {
            // process the subject
            OWLIndividual ind = ax.getSubject();
            TypedOWLIndividual source = cache.computeIfAbsent(ind, i -> toTypedIndividual(i, ont));

            // and the object
            ind = ax.getObject();
            TypedOWLIndividual target = cache.computeIfAbsent(ind, i -> toTypedIndividual(i, ont));

            g.addVertex(source);
            g.addVertex(target);
            g.addEdge(source, target, new OWLPropertyEdge(ax.getProperty()));
        });

        cache.clear();

        return g;
    }

    public static <V, E> List<GraphPath<V, E>> getPaths(Graph<V, E> g, V startNode, Integer maxLength) {
        return getPaths(g, Collections.singleton(startNode), maxLength);
    }

    public static <V, E> List<GraphPath<V, E>> getPaths(Graph<V, E> g, Set<V> startNodes, Integer maxLength) {
        return new AllPaths<>(g).getAllPaths(startNodes, true, maxLength);
    }

    public static <V, E extends LabeledEdge<T>, T> TreeMap<List<T>, Long> getPathsWithFrequencies(Graph<V, E> g,
                                                                                                  V startNode,
                                                                                                  Integer maxLength) {
        // sort by length
        Comparator<List<T>> c = Comparator
                .<List<T>>comparingInt(List::size)
                .thenComparing(Object::toString);
        return getPaths(g, startNode, maxLength).stream()  // compute paths
                .map(path -> path.getEdgeList().stream().map(LabeledEdge::getLabel).collect(Collectors.toList())) // map to edge sequences
                .collect(Collectors.groupingBy(Function.identity(), () -> new TreeMap<>(c), Collectors.counting())); // compute frequency per edge sequence
    }

    public static <V, E extends LabeledEdge<T>, T> Map<V, TreeMap<List<T>, Long>> getPathsWithFrequencies(Graph<V, E> g,
                                                                                                  Set<V> startNodes,
                                                                                                  Integer maxLength) {
        return startNodes.stream()
                .collect(Collectors.toMap(Function.identity(), v -> getPathsWithFrequencies(g, v, maxLength)));
    }


}
