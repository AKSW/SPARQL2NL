package org.aksw.sparql2nl.queryprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.core.VarAlloc;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprAggregator;
import com.hp.hpl.jena.sparql.expr.aggregate.AggCount;
import com.hp.hpl.jena.sparql.expr.aggregate.AggCountVar;
import com.hp.hpl.jena.sparql.expr.aggregate.AggCountVarDistinct;
import com.hp.hpl.jena.sparql.expr.aggregate.Aggregator;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitorBase;
import com.hp.hpl.jena.sparql.syntax.PatternVars;
import com.hp.hpl.jena.sparql.util.VarUtils;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

public class TypeExtractor extends ElementVisitorBase {
	
	private static final Node TYPE_NODE = Node.createURI(RDF.type.getURI());

	private List<Var> projectionVars;
	private Map<Var, Set<Triple>> var2Triples;
	
	private Map<String, Set<String>> var2TypesMap;
	
	private DomainExtractor domainExtractor;
	private RangeExtractor rangeExtractor;
	
	private boolean inferTypes = false;
	
	private Query query;
	private SparqlEndpoint endpoint;
	
	private boolean isCount = false;
	
	public TypeExtractor(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	public Map<String, Set<String>> extractTypes(Query query) {
		this.query = query;
		
		var2TypesMap = new HashMap<String, Set<String>>();
		var2Triples = new HashMap<Var, Set<Triple>>();
		projectionVars = query.getProjectVars();
		isCount = false;
		//handle COUNT aggregator by replacing generic var name with var name in COUNT construct
		for(Var v : new ArrayList<Var>(projectionVars)){
			if(query.getProject().hasExpr(v)){
				Expr expr = query.getProject().getExpr(v);
				if(expr instanceof ExprAggregator){
					Aggregator aggr = ((ExprAggregator) expr).getAggregator();
					if(aggr instanceof AggCountVar || aggr instanceof AggCountVarDistinct){
						projectionVars.remove(v);
						projectionVars.add(aggr.getExpr().asVar());
						isCount = true;
					}
				}
			}
		}
		
		//if query is ASK query use all variables
		if(query.isAskType()){
			projectionVars = new ArrayList<Var>(PatternVars.vars(query.getQueryPattern()));
		}
		
		ElementGroup wherePart = (ElementGroup) query.getQueryPattern();
		wherePart.visit(this);
		
		//give all projection vars which have no explicit type the most general type owl:Thing or rdfs:Datatype
		for(Var var : projectionVars){
			if(!var2TypesMap.containsKey(var.getName())){
				var2TypesMap.put(var.getName(), Collections.singleton(inferGenericType(var)));
			}
		}
		
		return var2TypesMap;
	}
	
	public boolean isCount() {
		return isCount;
	}
	
	/**
	 * Returns the generic type, i.e whether it is owl:Thing(entity) or rdfs:Literal(value)
	 * @param var
	 * @return
	 */
	private String inferGenericType(Var var){System.out.println(var);
		Set<Triple> triples = var2Triples.get(var);
	
		//if var is in subject position it should not be a literal, but an entity
		for(Triple triple : triples){
			if(triple.getSubject().sameValueAs(var)){
				return OWL.Thing.getURI();
			}
		}
		
		//TODO check if var is used in FILTER
		
		//else we try to infer the type by the predicate type, i.e. whether predicate is of type owl:Data(type)Property or owl:ObjectProperty
		for(Triple triple : triples){
			if(triple.getPredicate().isURI()){
				//if rdfs:label return rdfs:Literal
				if(triple.getPredicate().getURI().equals(RDFS.label.getURI())){
					return RDFS.Literal.getURI();
				}
				String type = getPropertyType(triple.getPredicate().getURI());
				if(type != null){
					if(type.equals(OWL.ObjectProperty.getURI())){
						return OWL.Thing.getURI();
					} else if(type.equals(OWL.DatatypeProperty.getURI())){
						return RDFS.Literal.getURI();
					}
				}
			} else {
				return RDF.Property.getURI();
			}
		}
		return OWL.Thing.getURI();
		
	}
	
	private String getPropertyType(String propertyURI){
		String query = String.format("SELECT ?type WHERE {<%s> a ?type}", propertyURI);
		ResultSet rs = new SparqlQuery(query, endpoint).send(false);
		while(rs.hasNext()){
			return rs.next().get("type").asResource().getURI();
		}
		return null;
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
			} else if(e instanceof ElementPathBlock){
				if(((ElementPathBlock) e).getPattern().getList().size() == 0){
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
			if(((ElementGroup)e).getElements().isEmpty() || ((ElementPathBlock)((ElementGroup)e).getElements().get(0)).isEmpty()){
				iterator.remove();
			}
			
		}
	}
	
	@Override
	public void visit(ElementOptional el) {
		//TODO handle separately
		el.getOptionalElement().visit(this);
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
						} else if(object.isVariable()){
							addType(subjectVar.getName(), OWL.Thing.getURI());
							addType(object.getName(), RDF.type.getURI());
							return true;
						}
						
					} else if(projectVar.equals(Var.alloc(object.getName()))){
						addType(object.getName(), RDF.type.getURI());
					}
					//TODO handle case where object is not a URI
					
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
		} else {
			for(Var var : VarUtils.getVars(triple)){
				Set<Triple> triples = var2Triples.get(var);
				if(triples == null){
					triples = new HashSet<Triple>();
					var2Triples.put(var, triples);
				}
				triples.add(triple);
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
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpediaLiveAKSW();
		
		TypeExtractor extr = new TypeExtractor(endpoint);
		extr.setDomainExtractor(new SPARQLDomainExtractor(endpoint));
		extr.setRangeExtractor(new SPARQLRangeExtractor(endpoint));
		
		String queryString = "PREFIX dbo:<http://dbpedia.org/ontology/> "
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
			"}";
		com.hp.hpl.jena.query.Query q = QueryFactory.create(queryString);
		System.out.println(extr.extractTypes(q));
		System.out.println(q);
	
		queryString = "PREFIX  res: <http://dbpedia.org/resource/>" +
				"PREFIX  dbo: <http://dbpedia.org/ontology/>" +
				"PREFIX  rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
				"SELECT DISTINCT  * WHERE{" +
				"{ res:Abraham_Lincoln dbo:deathPlace ?uri }" +
				"UNION" +
				"{ res:Abraham_Lincoln dbo:birthPlace ?uri }" +
				"?uri rdf:type dbo:Place " +
				"FILTER(regex(?uri, \"France\")) " +
				"OPTIONAL{ ?uri dbo:description ?x }  " +
				"} ";
		q = QueryFactory.create(queryString);
		System.out.println(extr.extractTypes(q));
		System.out.println(q);
		
		queryString = "PREFIX  res: <http://dbpedia.org/resource/>" +
		"PREFIX  dbo: <http://dbpedia.org/ontology/>" +
		"PREFIX  rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
		"ASK{" +
		"{ res:Abraham_Lincoln dbo:deathPlace ?uri }" +
		"UNION" +
		"{ res:Abraham_Lincoln dbo:birthPlace ?uri }" +
		"?uri rdf:type dbo:Place " +
		"FILTER(regex(?uri, \"France\")) " +
		"OPTIONAL{ ?uri dbo:description ?x }  " +
		"} ";
		q = QueryFactory.create(queryString, Syntax.syntaxARQ);
		System.out.println(extr.extractTypes(q));
		System.out.println(q);
		
		
	}
	
	
		
}
