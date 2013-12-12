package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.List;
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

import com.google.common.collect.Lists;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
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
	private String cacheDirectory = "cache/sparql";
	
	private File dereferencingCache;
	final HashFunction hf = Hashing.md5();
	
	boolean replaceUnderScores = true;
	
	private List<String> labelProperties = Lists.newArrayList(
			"http://www.w3.org/2000/01/rdf-schema#label",
			"http://xmlns.com/foaf/0.1/name");
	
	public URIConverter(SparqlEndpoint endpoint, String cacheDirectory) {
		this.endpoint = endpoint;
		this.cacheDirectory = cacheDirectory;
		
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
		dereferencingCache = new File(cacheDirectory, "dereferenced");
		dereferencingCache.mkdir();
	}
	
	public URIConverter(SparqlEndpoint endpoint, CacheCoreEx cacheBackend, String cacheDirectory) {
		this.endpoint = endpoint;
		this.cacheDirectory = cacheDirectory;
		
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		if(cacheBackend != null){
			CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
			qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
		}
		dereferencingCache = new File(cacheDirectory, "dereferenced");
		dereferencingCache.mkdir();
	}
	
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
		dereferencingCache = new File("dereferencing-cache");
		dereferencingCache.mkdir();
	}
	
	public URIConverter(Model model) {
		this.model = model;
		
		qef = new QueryExecutionFactoryModel(model);
	}
	
	/**
	 * @param labelProperties the labelProperties to set
	 */
	public void setLabelProperties(List<String> labelProperties) {
		this.labelProperties = labelProperties;
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
	        	//firstly, try to get the label from the endpoint
	            label = getLabel(uri);
	            //secondly, try to dereference the URI and search for the label in the returned triples
	            if(dereferenceURI && label == null && !uri.startsWith(XSD.getURI())){
	            	label = dereferenceURI(uri);
	            }
	            //fallback: use the short form of the URI
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
		if(replaceUnderScores){
			label = label.replace("_", " ");
		}
		return label;
		
	}
	
	private String getLabel(String uri){
		for (String labelProperty : labelProperties) {
			String labelQuery = "SELECT ?label WHERE {<" + uri + "> <" + labelProperty + "> ?label. FILTER (lang(?label) = 'en' )}";
			try {
				ResultSet rs = executeSelect(labelQuery);
				if(rs.hasNext()){
					return rs.next().getLiteral("label").getLexicalForm();
				}
			} catch (Exception e) {
				logger.warn("Getting label from SPARQL endpoint failed.", e);
			}
		}
		return null;
	}
	
	 /**
     * Returns the English label of the URI by dereferencing its URI and searching for rdfs:label entries.
     * @param uri
     * @return
     */
    private String dereferenceURI(String uri){
    	logger.debug("Dereferencing URI: " + uri);
    	try {
    		Model model = ModelFactory.createDefaultModel();
    		//try to find dereferenced file in cache
        	String hc = hf.newHasher().putString(uri).hash().toString();
        	File cachedFile = new File(dereferencingCache, hc + ".rdf");
        	if(cachedFile.exists()){
        		model.read(new FileInputStream(cachedFile), null, "RDF/XML");
        	} else {
        		URLConnection conn = new URL(uri).openConnection();
    			conn.setRequestProperty("Accept", "application/rdf+xml");
    			InputStream in = conn.getInputStream();
    			model.read(in, null);
    			in.close();
    			model.write(new FileOutputStream(cachedFile), "RDF/XML");
        	}
        	for (String labelProperty : labelProperties) {
        		for(Statement st : model.listStatements(model.getResource(uri), model.getProperty(labelProperty), (RDFNode)null).toList()){
    				Literal literal = st.getObject().asLiteral();
    				String language = literal.getLanguage();
    				if(language != null && language.equals("en")){
    					return literal.getLexicalForm();
    				}
    			}
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	return null;
    }
    
    private ResultSet executeSelect(String query){
    	ResultSet rs = qef.createQueryExecution(query).execSelect();
    	return rs;
    }
    
    public static void main(String[] args) {
    	URIConverter converter = new URIConverter(SparqlEndpoint.getEndpointDBpediaLiveAKSW());
		String label = converter.convert("http://dbpedia.org/resource/Nuclear_Reactor_Technology");
		System.out.println(label);
		label = converter.convert("http://dbpedia.org/resource/Woodroffe_School");
		System.out.println(label);
    }

}
