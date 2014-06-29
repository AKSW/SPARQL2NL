package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
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
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.util.SimpleIRIShortFormProvider;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.vocabulary.FOAF;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

/**
 * Converts a URI into its natural language representation.
 * @author Lorenz Buehmann
 *
 */
public class URIConverter {
	
	private static final Logger logger = Logger.getLogger(URIConverter.class.getName());
	
	private SimpleIRIShortFormProvider sfp = new SimpleIRIShortFormProvider();
	private LRUMap<String, String> uri2LabelCache = new LRUMap<String, String>(200);
	
	private QueryExecutionFactory qef;
	private String cacheDirectory = "cache/sparql";
	
	private File dereferencingCache;
	final HashFunction hf = Hashing.md5();
	
	private List<String> labelProperties = Lists.newArrayList(
			"http://www.w3.org/2000/01/rdf-schema#label",
			"http://xmlns.com/foaf/0.1/name");
	
	private String language = "en";

	//normalization options
	private boolean splitCamelCase = true;
	private boolean replaceUnderScores = true;
	private boolean toLowerCase = false;
	
	public URIConverter(SparqlEndpoint endpoint, String cacheDirectory) {
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
	
	public URIConverter(QueryExecutionFactory qef, String cacheDirectory) {
		this.qef = qef;
		this.cacheDirectory = cacheDirectory;
		
		
		dereferencingCache = new File(cacheDirectory, "dereferenced");
		dereferencingCache.mkdir();
	}
	
	public URIConverter(SparqlEndpoint endpoint, CacheCoreEx cacheBackend, String cacheDirectory) {
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
		qef = new QueryExecutionFactoryModel(model);
	}
	
	/**
	 * @param labelProperties the labelProperties to set
	 */
	public void setLabelProperties(List<String> labelProperties) {
		this.labelProperties = labelProperties;
	}
	
	/**
	 * Convert a URI into a natural language representation.
	 * @param uri the URI to convert
	 * @return the natural language representation of the URI
	 */
	public String convert(String uri){
		return convert(uri, false);
	}
	
	/**
	 * Convert a URI into a natural language representation.
	 * @param uri the URI to convert
	 * @param dereferenceURI whether to try Linked Data dereferencing of the URI
	 * @return the natural language representation of the URI
	 */
	public String convert(String uri, boolean dereferenceURI){
		if (uri.equals(RDF.type.getURI())) {
            return "type";
        } else if (uri.equals(RDFS.label.getURI())) {
            return "label";
        }
		if(uri.equals("http://dbpedia.org/ontology/phylum")){
			return "phylum";
		}
		
		//check if already cached
		String label = uri2LabelCache.get(uri);
		
		//if not in cache
		if(label == null){
			//1. check if it's some built-in resource
			try {
				label = getLabelFromBuiltIn(uri);
			} catch (Exception e) {
				logger.error("Getting label for " + uri + " from knowledge base failed.", e);
			}
			
			//2. try to get the label from the endpoint
			if(label == null){
				 try {
						label = getLabelFromKnowledgebase(uri);
					} catch (Exception e) {
						logger.error("Getting label for " + uri + " from knowledge base failed.", e);
					}
			}
            
            //3. try to dereference the URI and search for the label in the returned triples
            if(dereferenceURI && label == null && !uri.startsWith(XSD.getURI())){
            	try {
					label = getLabelFromLinkedData(uri);
				} catch (Exception e) {
					logger.error("Dereferencing of " + uri + "failed.");
				}
            }
            
            //4. use the short form of the URI
            if(label == null){
            	try {
					label = sfp.getShortForm(IRI.create(URLDecoder.decode(uri, "UTF-8")));
				} catch (UnsupportedEncodingException e) {
					logger.error("Getting short form of " + uri + "failed.", e);
				}
            }
            
            //5. use the URI
            if(label == null){
            	label = uri;
            }
            
            //do some normalization, e.g. remove underscores
            if(replaceUnderScores){
    			label = label.replace("_", " ");
    		}
            if(splitCamelCase){
            	label = splitCamelCase(label);
            }
            if(toLowerCase){
            	label = label.toLowerCase();
            }
		}
	    
		//put into cache
		uri2LabelCache.put(uri, label);
		
		return label;
	}
	
	private String getLabelFromBuiltIn(String uri){
		if(uri.startsWith(XSD.getURI()) 
				|| uri.startsWith(OWL.getURI()) 
				|| uri.startsWith(RDF.getURI())
				|| uri.startsWith(RDFS.getURI())
				|| uri.startsWith(FOAF.getURI())) {
			try {
				String label = sfp.getShortForm(IRI.create(URLDecoder.decode(uri, "UTF-8")));
				 //if it is a XSD numeric data type, we attach "value"
	            if(uri.equals(XSD.nonNegativeInteger.getURI()) || uri.equals(XSD.integer.getURI())
	            		|| uri.equals(XSD.negativeInteger.getURI()) || uri.equals(XSD.decimal.getURI())
	            		|| uri.equals(XSD.xdouble.getURI()) || uri.equals(XSD.xfloat.getURI())
	            		|| uri.equals(XSD.xint.getURI()) || uri.equals(XSD.xshort.getURI())
	            		|| uri.equals(XSD.xbyte.getURI()) || uri.equals(XSD.xlong.getURI())
	            		){
	            	label += " value";
	            }
				if(replaceUnderScores){
	    			label = label.replace("_", " ");
	    		}
	            if(splitCamelCase){
	            	label = splitCamelCase(label);
	            }
	            if(toLowerCase){
	            	label = label.toLowerCase();
	            }
	            return label;
			} catch (UnsupportedEncodingException e) {
				logger.error("Getting short form of " + uri + "failed.", e);
			}
		}
		return null;
	}
	
	private String getLabelFromKnowledgebase(String uri){
		for (String labelProperty : labelProperties) {
			String labelQuery = "SELECT ?label WHERE {<" + uri + "> <" + labelProperty + "> ?label. FILTER (lang(?label) = '" + language + "' )}";
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
    private String getLabelFromLinkedData(String uri){
    	logger.debug("Dereferencing URI: " + uri);
    	try {
    		//1. get triples for the URI by sending a Linked Data request
    		Model model = ModelFactory.createDefaultModel();
    		//try to find dereferenced file in cache
        	String hc = hf.newHasher().putString(uri, Charsets.UTF_8).hash().toString();
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
        	logger.debug("Got " + model.size() + " triples for " + uri);
        	
        	//2. check if we find a label in the triples
        	for (String labelProperty : labelProperties) {
        		for(Statement st : model.listStatements(model.getResource(uri), model.getProperty(labelProperty), (RDFNode)null).toList()){
    				Literal literal = st.getObject().asLiteral();
    				String language = literal.getLanguage();
    				if(language != null && language.equals(language)){
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
    
    public static String splitCamelCase(String s) {
    	StringBuilder sb = new StringBuilder();
    	for (String token : s.split(" ")) {
			sb.append(StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(token), ' ')).append(" ");
		}
    	return sb.toString().trim();
//    	return s.replaceAll(
//    	      String.format("%s|%s|%s",
//    	         "(?<=[A-Z])(?=[A-Z][a-z])",
//    	         "(?<=[^A-Z])(?=[A-Z])",
//    	         "(?<=[A-Za-z])(?=[^A-Za-z])"
//    	      ),
//    	      " "
//    	   );
    	}
    
    private ResultSet executeSelect(String query){
    	ResultSet rs = qef.createQueryExecution(query).execSelect();
    	return rs;
    }
    
    public static void main(String[] args) {
    	URIConverter converter = new URIConverter(SparqlEndpoint.getEndpointDBpedia());
		String label = converter.convert("http://dbpedia.org/resource/Nuclear_Reactor_Technology");
		System.out.println(label);
		label = converter.convert("http://dbpedia.org/resource/Woodroffe_School");
		System.out.println(label);
		label = converter.convert("http://dbpedia.org/ontology/isBornIn", true);
		System.out.println(label);
		label = converter.convert("http://www.w3.org/2001/XMLSchema#integer");
		System.out.println(label);
    }

}
