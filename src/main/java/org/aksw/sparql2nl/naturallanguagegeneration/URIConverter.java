package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.collections.map.LRUMap;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.util.SimpleIRIShortFormProvider;

import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

public class URIConverter {
	
	
	private static final Logger logger = Logger.getLogger(URIConverter.class.getName());
	
	private SimpleIRIShortFormProvider sfp = new SimpleIRIShortFormProvider();
	private SparqlEndpoint endpoint;
	private Model model;
	private LRUMap uri2LabelCache = new LRUMap(50);
	
	public URIConverter(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
	}
	
	public URIConverter(Model model) {
		this.model = model;
	}
	
	public String convert(String uri){
		return convert(uri, true);
	}
	
	public String convert(String uri, boolean dereferenceURI){
		if (uri.equals(RDF.type.getURI())) {
            return "type";
        } else if (uri.equals(RDFS.label.getURI())) {
            return "label";
        }
		
		String label = (String) uri2LabelCache.get(uri);
		if(label == null){
	        try {
	            String labelQuery = "SELECT ?label WHERE {<" + uri + "> "
	                    + "<http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER (lang(?label) = 'en' )}";

	            try {
					// take care of graph issues. Only takes one graph. Seems like some sparql endpoint do
					// not like the FROM option.
					ResultSet results = executeSelect(labelQuery);

					//get label from knowledge base
					QuerySolution soln;
					while (results.hasNext()) {
					    soln = results.nextSolution();
					    // process query here
					    {
					        label = soln.getLiteral("label").getLexicalForm();
					    }
					}
				} catch (Exception e) {
					logger.warn("Getting label from SPARQL endpoint failed.", e);
				}
	            if(dereferenceURI && label == null && !uri.startsWith(XSD.getURI())){
	            	label = dereferenceURI(uri);
	            }
	            if(label == null){
	            	label = sfp.getShortForm(IRI.create(uri));
	            }
	            //if it is a number we attach "Number"
	            if(uri.equals(XSD.nonNegativeInteger.getURI()) || uri.equals(XSD.integer.getURI())
	            		|| uri.equals(XSD.negativeInteger.getURI()) || uri.equals(XSD.decimal.getURI())
	            		|| uri.equals(XSD.xdouble.getURI()) || uri.equals(XSD.xfloat.getURI())
	            		|| uri.equals(XSD.xint.getURI()) || uri.equals(XSD.xshort.getURI())
	            		|| uri.equals(XSD.xbyte.getURI()) || uri.equals(XSD.xlong.getURI())
	            		){
	            	label += "Value";
	            }
	            	
	            
	            if(label == null){
	            	label = uri;
	            }
	            uri2LabelCache.put(uri, label);
	            return label;
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        return uri;
		}
		return label;
		
	}
	
	 /**
     * Returns the English label of the URI by dereferencing its URI and searching for rdfs:label entries.
     * @param uri
     * @return
     */
    private String dereferenceURI(String uri){
    	logger.debug("Dereferencing URI: " + uri);
    	String label = null;
    	try {
			URLConnection conn = new URL(uri).openConnection();
			conn.setRequestProperty("Accept", "application/rdf+xml");
			Model model = ModelFactory.createDefaultModel();
			InputStream in = conn.getInputStream();
			model.read(in, null);
			for(Statement st : model.listStatements(model.getResource(uri), RDFS.label, (RDFNode)null).toList()){
				Literal literal = st.getObject().asLiteral();
				String language = literal.getLanguage();
				if(language != null && language.equals("en")){
					label = literal.getLexicalForm();
				}
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	return label;
    }
    
    private ResultSet executeSelect(String query){
    	ResultSet rs;
    	if(endpoint != null){
    		QueryEngineHTTP qexec = new QueryEngineHTTP(endpoint.getURL().toString(), query);
        	qexec.setDefaultGraphURIs(endpoint.getDefaultGraphURIs());
        	rs = qexec.execSelect();
    	} else {
    		rs = QueryExecutionFactory.create(query, model).execSelect();
    	}
    	
    	return rs;
    }
    
    public static void main(String[] args) {
		String label = new URIConverter(SparqlEndpoint.getEndpointDBpediaLiveAKSW()).convert("http://dbpedia.org/resource/Nuclear_Reactor_Technology");
		System.out.println(label);
		label = new URIConverter(SparqlEndpoint.getEndpointDBpediaLiveAKSW()).convert("http://dbpedia.org/ontology/birthDate");
		System.out.println(label);
    }

}
