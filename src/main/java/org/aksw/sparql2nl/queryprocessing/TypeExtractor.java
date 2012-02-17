package org.aksw.sparql2nl.queryprocessing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dllearner.kb.sparql.SparqlEndpoint;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitorBase;
import com.hp.hpl.jena.vocabulary.RDF;

public class TypeExtractor extends ElementVisitorBase {
	
	private static final Node TYPE_NODE = Node.createURI(RDF.type.getURI());

	private List<Var> projectionVars;
	
	private Map<String, Set<String>> var2TypesMap = new HashMap<String, Set<String>>();
	
	private DomainExtractor domainExtractor;
	private RangeExtractor rangeExtractor;
	
	private boolean inferTypes = false;
	

	public Map<String, Set<String>> extractTypes(Query query) {
		projectionVars = query.getProjectVars();
		
		ElementGroup wherePart = (ElementGroup) query.getQueryPattern();
		wherePart.visit(this);
		
		return var2TypesMap;
	}
	
	public void setDomainExtractor(DomainExtractor domainExtractor) {
		this.domainExtractor = domainExtractor;
	}
	
	public void setRangeExtractor(RangeExtractor rangeExtractor) {
		this.rangeExtractor = rangeExtractor;
	}
	
	public void setInferTypes(boolean inferTypes) {
		this.inferTypes = inferTypes;
	}

	@Override
	public void visit(ElementGroup el) {
		ElementPathBlock bgp = null;
		for (Iterator<Element> iterator = el.getElements().iterator(); iterator.hasNext();) {
			Element e = iterator.next();
			e.visit(this);
			if(e instanceof ElementUnion){
				if(((ElementUnion) e).getElements().size() == 1){
					bgp = (ElementPathBlock) ((ElementGroup)((ElementUnion) e).getElements().get(0)).getElements().get(0);
					iterator.remove();
				}
			}
		}
		if(bgp != null){
			el.addElement(bgp);
		}
	}

	@Override
	public void visit(ElementTriplesBlock el) {
		for (Iterator<Triple> iter = el.patternElts(); iter.hasNext();) {
			Triple t = iter.next();
			processTriple(t);
		}
	}

	@Override
	public void visit(ElementPathBlock el) {
		for (Iterator<TriplePath> iter = el.patternElts(); iter.hasNext();) {
			TriplePath tp = iter.next();
			if (tp.isTriple()) {
				Triple t = tp.asTriple();
				boolean toRemove = processTriple(t);
				if(toRemove){
					iter.remove();
				}
			}
		}
	}
	
	@Override
	public void visit(ElementUnion el) {
		for (Iterator<Element> iterator = el.getElements().iterator(); iterator.hasNext();) {
			Element e = iterator.next();
			e.visit(this);
			if(((ElementPathBlock)((ElementGroup)e).getElements().get(0)).isEmpty()){
				iterator.remove();
			}
			
		}
	}
	
	/**
	 * Returns TRUE if triple has as predicate rdf:type and a projection variable in the subject position, otherwise FALSE.
	 * @param triple
	 * @return
	 */
	private boolean processTriple(Triple triple){
		Node subject = triple.getSubject();
		Node object = triple.getObject();
		
		if (triple.predicateMatches(TYPE_NODE)) {//process rdf:type triples	
			if(subject.isVariable()){
				Var subjectVar = Var.alloc(subject.getName());
				for(Var projectVar : projectionVars){
					if(projectVar.equals(subjectVar)){
						if(object.isURI()){
							addType(subjectVar.getName(), object.getURI());
							return true;
						}
						//TODO handle case where object is not a URI
					}
					
				}
			}
		} else if(inferTypes && triple.getPredicate().isURI()){//process triples where predicate is not rdf:type, i.e. use rdfs:domain and rdfs:range for inferencing the type
			Node predicate = triple.getPredicate();
			for(Var projectVar : projectionVars){
				if(subject.isVariable() && projectVar.equals(Var.alloc(subject.getName()))){
					if(domainExtractor != null){
						String domain = domainExtractor.getDomain(predicate.getURI());
						if(domain != null){
							addType(subject.getName(), domain);
						}
					}
				} else if(object.isVariable() && projectVar.equals(Var.alloc(object.getName()))){
					if(rangeExtractor != null){
						String range = rangeExtractor.getRange(predicate.getURI());
						if(range != null){
							addType(object.getName(), range);
						}
					}
				}
			}
		}
		return false;
	}
	
	private void addType(String variable, String type){
		Set<String> types = var2TypesMap.get(variable);
		if(types == null){
			types = new HashSet<String>();
			var2TypesMap.put(variable, types);
		}
		types.add(type);
	}
	
	public static void main(String[] args) {
		com.hp.hpl.jena.query.Query q = QueryFactory
				.create("PREFIX dbo:<http://dbpedia.org/ontology/> "
						+ "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> "
						+ "SELECT ?s ?o1 WHERE {" 
						+ "{?s ?p ?o. "
						+ "?s1 ?p ?o1. " 
						+ "?s a dbo:Book."
						+ "?s a ?y. ?y rdfs:subClassOf dbo:Film."
						+ "?o1 a dbo:Bridge." 
						+ "?o1 a dbo:Musican."
						+ "?s dbo:birthPlace ?o2."
						+ "?o a dbo:City.}" +
						"UNION{?s a dbo:Table.}" +
						"}");
		
		TypeExtractor extr = new TypeExtractor();
		
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpediaLiveAKSW();
		extr.setDomainExtractor(new SPARQLDomainExtractor(endpoint));
		extr.setRangeExtractor(new SPARQLRangeExtractor(endpoint));
		System.out.println(extr.extractTypes(q));
		System.out.println(q);
	}
	
	
		
}
