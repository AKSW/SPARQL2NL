
package org.aksw.sparql2nl.smooth_nlg;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.SortCondition;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.expr.ExprAggregator;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.PatternVars;
import com.hp.hpl.jena.sparql.util.VarUtils;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import java.util.*;
import org.aksw.sparql2nl.queryprocessing.TypeExtractor;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

/**
 *
 * @author christina
 */
public class SPARQLDeconstructor {
    
    private SparqlEndpoint endpoint;
    private Query query;
    private TypeExtractor tEx;
    
    public SPARQLDeconstructor(SparqlEndpoint e) {
        endpoint = e;
    }
    
    public CardBox deconstruct(Query q) {
        
        query = q;
        tEx = new TypeExtractor(endpoint);
        Map<String,Set<String>> typeMap = tEx.extractTypes(query);
                
        List<Element> body = getWhereElements(query);
        List<String> triples = new ArrayList<String>();
        List<Element> filters = new ArrayList<Element>();
        List<Element> optionals = new ArrayList<Element>();
        
        for (Element e : body) {
            if (e instanceof ElementOptional) {
                optionals.add(e);
            } else if (e.toString().startsWith("FILTER")) {
                filters.add(e);
            } else {
                for (String triple : e.toString().split("\\s\\.\\s")) {
                    triples.add(triple);
                }
            }
        }
        
        List<OrderBy> orderBys = new ArrayList<OrderBy>();
        if (query.getOrderBy() != null) {
            long offset = 0; long limit = 0; 
            if (query.hasOffset()) { offset = query.getOffset(); }
            if (query.hasLimit()) { limit = query.getLimit(); }
            for (SortCondition sc : query.getOrderBy()) {
                if (sc.getDirection() != 0) {
                    orderBys.add(new OrderBy(sc.getExpression().toString().replace("?",""),sc.getDirection(),offset,limit));
                } else {
                    orderBys.add(new OrderBy(sc.getExpression().toString().replace("?",""),0));
                }
            }
        }

        List<String> optionalVars = new ArrayList<String>();
        for (Element e : optionals) {
            for (Var var : PatternVars.vars(e)) {
                optionalVars.add(var.toString().replace("?",""));
            }
        }
        List<String> nonoptionalVars = new ArrayList<String>();
        for (Element e : body) {
            for (Var var : PatternVars.vars(e)) {
                if (!optionalVars.contains(var.toString())) {
                    nonoptionalVars.add(var.toString().replace("?",""));
                }
            }
        }
        
        // BUILD PRIMARY ENTITIES for variables in SELECT clause        
        List<Entity> primaries = new ArrayList<Entity>();
        for (String v : typeMap.keySet()) {
            if (nonoptionalVars.contains(v) && !optionalVars.contains(v)) {
                primaries.add(buildEntity(v,typeMap,body,triples));
            }
        }

        // BUILD SECONDARY ENTITIES for all other variables occurring in the query
        List<Entity> secondaries = new ArrayList<Entity>();
        for (String v : nonoptionalVars) {
            if (!typeMap.containsKey(v.toString())) {
                secondaries.add(buildEntity(v.toString(),typeMap,body,triples));
            }
        }
        
        return new CardBox(primaries,secondaries,filters,optionals,orderBys);
    }
    
    private Entity buildEntity(String v,Map<String,Set<String>> typeMap,List<Element> body,List<String> triples) {
        
        String var; 
        boolean count; 
        String type; 
        Set<Predicate> properties = new HashSet<Predicate>();
        
        var = v;
            // Is it like COUNT(?v) ?
            count = false;
            for (ExprAggregator agg : query.getAggregators()) {
                if (agg.asSparqlExpr().equals("count(?"+v+")") || agg.asSparqlExpr().equals("count(distinct ?"+v+")")) {
                   count = true;
                } 
            }           
            // Determine type. // TODO cases with more than one type?
            type = "value"; // TODO what if xsd:date or xsd:double? not returned from type extractor!
            if (typeMap.containsKey(v)) {
            for (String t : typeMap.get(v)) {
                if (t.equals(OWL.Thing.getURI())) {
                    type = "thing";
                } else if (t.equals(RDFS.Literal.getURI())) {
                    type = "literal";
                } else {
                    type = getEnglishLabel(t); 
                }
                break;
            }}
            // Collect properties.
            List<String> usedTriples = new ArrayList<String>();
            for (String triple : triples) {
                if (triple.contains("?"+v)) { // WARNING potential to confuse ?v and ?v1
                    String[] spo = triple.toString().trim().split(" ");
                    if (spo.length == 3) {
                        properties.add(new Predicate(spo[0],spo[1],spo[2]));
                        usedTriples.add(triple);
                    }
                }
            }
            triples.removeAll(usedTriples);

            return new Entity(var,count,type,properties);       
    }
    
    
    
    private static List<Element> getWhereElements(Query query) {
        List<Element> result = new ArrayList<Element>();
        ElementGroup elt = (ElementGroup) query.getQueryPattern();
        for (int i = 0; i < elt.getElements().size(); i++) {
            Element e = elt.getElements().get(i);
            if (!(e instanceof ElementOptional)) {
                result.add(e);
            }
        }
        return result;
    }
    
    private String getEnglishLabel(String resource) {
        if(resource.equals(RDF.type.getURI())){
            return "type";
        } else if(resource.equals(RDFS.label.getURI())){
        	return "label";
        }
        try {
            String labelQuery = "SELECT ?label WHERE {<" + resource + "> "
                    + "<http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER (lang(?label) = 'en')}";

            // take care of graph issues. Only takes one graph. Seems like some sparql endpoint do
            // not like the FROM option.
            ResultSet results = new SparqlQuery(labelQuery, endpoint).send();

            //get label from knowledge base
            String label = null;
            QuerySolution soln;
            while (results.hasNext()) {
                soln = results.nextSolution();
                // process query here
                {
                    label = soln.getLiteral("label").getLexicalForm();
                }
            }
            return label;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
