package org.aksw.sparql2nl.queryprocessing;

import java.util.Iterator;
import java.util.Stack;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitorBase;

public class DisjunctiveNormalFormConverter2 extends ElementVisitorBase {
	
	private boolean inUNION = false;
	private boolean innerUnion = false;
	private boolean outerUnion = true;
	
	private Stack<ElementUnion> unionStack = new Stack<ElementUnion>();
	
	public com.hp.hpl.jena.query.Query getDisjunctiveNormalForm(com.hp.hpl.jena.query.Query query){
		Query copy = QueryFactory.create(query);
		copy.getQueryPattern().visit(this);
		
		return copy;
	}
	
	@Override
	public void visit(ElementUnion el) {
		unionStack.push(el);
		for(Element e : el.getElements()){
			e.visit(this);
		}
	}
	
	@Override
	public void visit(ElementPathBlock el) {
		
	}
	
	@Override
	public void visit(ElementOptional el) {
		// TODO Auto-generated method stub
		super.visit(el);
	}
	
	@Override
	public void visit(ElementGroup el) {
		Element e;
		ElementUnion removed = null;
		ElementUnion top = null;
		for(Iterator<Element> it = el.getElements().iterator(); it.hasNext();){
			e = it.next();
			e.visit(this);
			if(e instanceof ElementUnion){
				top = unionStack.pop();
				if(!unionStack.isEmpty()){
					it.remove();
					removed = (ElementUnion) e;
				}
			}
		}
		if(removed != null){
			for(Element subEl : removed.getElements()){
				ElementGroup g = new ElementGroup();
				for(Element subEl2 : el.getElements()){
					g.addElement(subEl2);
				}
				g.addElement(subEl);
				el.addElement(g);
			}
		}
	}
	
	public static void main(String[] args) {
		String queryString = "PREFIX dbo: <http://dbpedia.org/ontology/> " +
				"PREFIX dbp: <http://dbpedia.org/property/> " +
				"PREFIX res: <http://dbpedia.org/resource/> " +
				"PREFIX yago: <http://dbpedia.org/class/yago/> " +
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
				"SELECT DISTINCT ?uri ?string WHERE {" +
				"        { ?uri rdf:type yago:ArgentineFilms . }        " +
				"UNION	{ ?uri rdf:type dbo:Film .	" +
				"{ ?uri dbo:country res:Argentina . } " +
				"UNION " +
				"{ ?uri dbp:country 'Argentina'@en . } }	" +
				"OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') }" +
				"}";
		
//		queryString = "PREFIX dbo: <http://dbpedia.org/ontology/> " +
//				"PREFIX dbp: <http://dbpedia.org/property/> " +
//				"PREFIX res: <http://dbpedia.org/resource/> " +
//				"PREFIX yago: <http://dbpedia.org/class/yago/> " +
//				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
//				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
//				"SELECT DISTINCT ?uri ?string WHERE {" +
//				"?uri rdf:type dbo:Automobile .         " +
//				" { ?uri dbp:production res:Germany . } " +
//						"UNION " +
//				"{ ?uri dbp:assembly res:Germany . }        " +
//						"UNION { ?uri dbp:manufacturer ?x .       " +
//								" { ?x dbo:locationCountry res:Germany . } " +
//									"UNION " +
//								"{ ?x rdf:type yago:AutomotiveCompaniesOfGermany . } " +
//								"}        " +
//				"OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') }}";
		System.out.println(QueryFactory.create(queryString, Syntax.syntaxARQ));
		Query q = QueryFactory.create(queryString, Syntax.syntaxARQ);
		q = new DisjunctiveNormalFormConverter2().getDisjunctiveNormalForm(q);
		System.out.println(q.toString());
	}
	

}
