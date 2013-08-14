package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreH2;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.apache.commons.collections.map.LRUMap;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.util.SimpleIRIShortFormProvider;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

public class URIConverter {
	
	
	private static final Logger logger = Logger.getLogger(URIConverter.class.getName());
	
	private SimpleIRIShortFormProvider sfp = new SimpleIRIShortFormProvider();
	private SparqlEndpoint endpoint;
	private Model model;
	private LRUMap uri2LabelCache = new LRUMap(50);
	
	private static QueryExecutionFactory qef;
	private static String cacheDirectory = "cache/sparql";
	
	public URIConverter(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
		
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		if(cacheDirectory != null){
			try {
				long timeToLive = TimeUnit.DAYS.toMillis(30);
				CacheCoreEx cacheBackend = CacheCoreH2.create(cacheDirectory, timeToLive, true);
				CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
				qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public URIConverter(Model model) {
		this.model = model;
		
		qef = new QueryExecutionFactoryModel(model);
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
	            	label = sfp.getShortForm(IRI.create(URLDecoder.decode(uri, "UTF-8")));
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
    	ResultSet rs = qef.createQueryExecution(query).execSelect();
    	return rs;
    }
    
    public static void main(String[] args) {
    	URIConverter converter = new URIConverter(SparqlEndpoint.getEndpointDBpediaLiveAKSW());
		String label = converter.convert("http://dbpedia.org/resource/Nuclear_Reactor_Technology");
		System.out.println(label);
		label = converter.convert("http://dbpedia.org/ontology/birthDate");
		System.out.println(label);
    }

}
