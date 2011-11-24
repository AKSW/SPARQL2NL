/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.queryprocessing;

import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.aksw.sparql2nl.queryprocessing.Similarity.SimilarityMeasure;
import simpack.accessor.graph.SimpleGraphAccessor;
import simpack.api.IGraphNode;
import simpack.util.graph.GraphNode;

/**
 * Processes a SPARQL query and stores information such as 
 * - query with only variables
 * - mapping of variables to URIs
 * @author ngonga
 */
public class Query {

    private String originalQuery;
    //original query where all non variables were replaces by variables
    private String queryWithOnlyVars;
    //maps variables to their values in the original query
    private HashMap<String, String> nonVar2Var;
    private HashMap<String, String> var2NonVar;
    //exception that are not to be replaced
    private HashMap<String, String> exceptions;
    private TreeSet<String> selectedVars;
    private SimpleGraphAccessor graphRepresentation;
    private boolean usesGroupBy;
    private boolean usesLimit;
    private boolean usesCount;
    private boolean usesSelect;

    public boolean getUsesSelect() {
        return usesSelect;
    }

    public boolean getUsesCount() {
        return usesCount;
    }

    public TreeSet<String> getSelectedVars() {
        return selectedVars;
    }

    public boolean getUsesGroupBy() {
        return usesGroupBy;
    }
    
    public boolean getUsesLimit() {
        return usesLimit;
    }

    public Query(String sparqlQuery) {
        originalQuery = sparqlQuery;
        queryWithOnlyVars = null;
        var2NonVar = null;
        nonVar2Var = null;
        exceptions = new HashMap<String, String>();
        // non-variables that are to be left as is
//        exceptions.put("rdfs:label", "\\?\\?1");
//        exceptions.put("\\?\\?1", "rdfs:label");
        exceptions.put("rdf:type", "\\!\\!2");
        exceptions.put("\\!\\!2", "rdf:type");
        if (sparqlQuery.toLowerCase().contains("group ")) {
            usesGroupBy = true;
        } else {
            usesGroupBy = false;
        }

        if (sparqlQuery.toLowerCase().contains("limit ")) {
            usesLimit = true;
        } else {
            usesLimit = false;
        }

        if (sparqlQuery.toLowerCase().contains("count")) {
            usesCount = true;
        } else {
            usesCount = false;
        }
        
        if (sparqlQuery.toLowerCase().contains("select ")) {
            usesSelect = true;
        } else {
            usesSelect = false;
        }
        replaceNonVariables();
        getGraphRepresentation();        
    }

    // Replaces non-variables that are not members of the exception with variables
    private void replaceNonVariables() {
        String copy = originalQuery;
                
        copy = copy.replaceAll("\n", " ");
        if(!copy.contains("{ ")) copy = copy.replaceAll(Pattern.quote("{"), "{ ");
        if(!copy.contains(" }")) copy = copy.replaceAll(Pattern.quote("}"), " }");
        copy = copy.replaceAll(Pattern.quote(". "), " . ");
        var2NonVar = new HashMap<String, String>();
        nonVar2Var = new HashMap<String, String>();

        //copy = copy.substring(copy.indexOf("{")+1,  copy.indexOf("}"));
        //1. Replaces the exceptions by exception tokens
        for (String key : exceptions.keySet()) {
            if (copy.contains(key) && !key.startsWith("\\!\\!")) {
                copy = copy.replaceAll(key, exceptions.get(key));
            }
        }

        //2. Replace everything that contains : with a var. 
        //Will assume that aaa is never used a var label
        String split[] = copy.split(" ");
        copy = copy.trim();
        int counter = 0;
        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].trim();
            //ignore prefixes
            while (split[i].equalsIgnoreCase("PREFIX")) {                
                //System.out.println(split[i]);
                i = i + 3;                
                //System.out.println(split[i]);
            } 
            if (split[i].contains(":") || (split[i].contains("?") && !split[i].contains("?aaa"))) {                
                if (!nonVar2Var.containsKey(split[i])) {
                    nonVar2Var.put(split[i], "?aaa" + counter);
                    var2NonVar.put("?aaa" + counter, split[i]);
                    //if(split[i].endsWith("\\."))
                    //copy = copy.replaceAll(Pattern.quote(split[i]), "?aaa" + counter +".");
                    copy = copy.replaceAll(Pattern.quote(split[i])+" ", "?aaa" + counter+" ");
                    counter++;
                }
            }
        }
        //3. get selectedVars
        if(usesSelect)
        {
            selectedVars = new TreeSet<String>();
            String vars = copy.substring(copy.toLowerCase().indexOf("select ")+7, 
                    copy.toLowerCase().indexOf(" where"));
            split = vars.trim().split(" ");
            for(int i=0; i<split.length; i++)
            {
                if(split[i].length()>1)
                    selectedVars.add(split[i].trim());
            }
        }
        queryWithOnlyVars = copy;
        for (String key : exceptions.keySet()) {
            if (key.startsWith("\\!\\!")) {
                queryWithOnlyVars = queryWithOnlyVars.replaceAll(key, exceptions.get(key));
            }
        }        
    }

    //Computes a simple graph representation of the query
    // is only computed once and then stored
    public SimpleGraphAccessor getGraphRepresentation() {
        if (graphRepresentation != null) {
            return graphRepresentation;
        }
        HashMap<String, GraphNode> nodeIndex = new HashMap<String, GraphNode>();
        SimpleGraphAccessor graph = new SimpleGraphAccessor();
        String selectSection = queryWithOnlyVars.substring(queryWithOnlyVars.indexOf("{") + 1,
                queryWithOnlyVars.indexOf("}"));
        //split select section into single statements
        selectSection = selectSection.trim();
        String[] statements = selectSection.split(Pattern.quote("."));
        //generate a graph. For each spo generate 3 nodes (s, p and o) and
        //two edges (s, p) and (p, o)
        for (int i = 0; i < statements.length; i++) {
            String[] variables = statements[i].trim().split(" ");
            //ensure that we have the right mapping between variables and graph node
            for (int j = 0; j < variables.length; j++) {
                if (!nodeIndex.containsKey(variables[j])) {
                    nodeIndex.put(variables[j], new GraphNode(variables[j]));
                    graph.addNode(nodeIndex.get(variables[j]));
                }
                if (j > 0) {
                    graph.setEdge(nodeIndex.get(variables[j - 1]), nodeIndex.get(variables[j]));
                }
            }
        }
        //System.out.println(graph.getNodeSet());
        //System.out.println(graph.);
        graphRepresentation = graph;
        return graph;
    }

    /** Computes a simple string representation of the input
     * 
     * @param g Input graph
     * @return String representation
     */
    public static String getStringRepresentation(SimpleGraphAccessor g) {
        
        String result = "";
        result = result +"Nodeset = "+g.getNodeSet() + "\n<";
        for (IGraphNode node : g.getNodeSet()) {
            for (IGraphNode node2 : node.getSuccessorSet()) {
                result = result + node.getLabel() + " -> " + node2.getLabel() + "\n";
            }
        }
        return result+">";
    }

    public HashMap<String, String> getNonVar2Var() {
        return nonVar2Var;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public String getQueryWithOnlyVars() {
        return queryWithOnlyVars;
    }

    public HashMap<String, String> getVar2NonVar() {
        return var2NonVar;
    }

    //just for tests
    public static void main(String args[]) {
//        String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
//"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
//"ASK WHERE {\n"+
//"?y rdf:type ?p13736.\n"+
//"}\n";
        String query = "SELECT ?x WHERE {?x ?a ?b. ?x ?c ?d}"; 

        String query2 = "SELECT ?x WHERE {?x ?a ?b. ?x rdf:type ?d}"; 
        Query q1 = new Query(query);
        Query q2 = new Query(query2);
        //Query q2 = new Query(query2);
//        System.out.println(q1.originalQuery);
//        System.out.println(q1.queryWithOnlyVars);
//        System.out.println(q1.nonVar2Var);
//        System.out.println(q1.var2NonVar);
//        System.out.println(q1.exceptions);
//        //q1.getGraphRepresentation();
        System.out.println("\n---\n" + getStringRepresentation(q1.getGraphRepresentation()));
        System.out.println("\n---\n" + getStringRepresentation(q2.getGraphRepresentation()));
        System.out.println(Similarity.getSimilarity(q1, q2, SimilarityMeasure.GRAPH_ISOMORPHY));
        System.out.println(Similarity.getSimilarity(q1, q2, SimilarityMeasure.TYPE_AWARE_ISOMORPHY));
    }
}
