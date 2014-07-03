/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLConnection;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.WebContent;
import org.apache.log4j.Logger;

import com.google.common.net.UrlEscapers;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * Class to retrieve triples based on the Linked Data dereferencing paradigm.
 * @author Lorenz Buehmann
 *
 */
public class URIDereferencer {
	
	private static final Logger logger = Logger.getLogger(URIDereferencer.class.getName());
	
	//the content type used in the accept header
	private String contentType = WebContent.contentTypeRDFXML;
	
	//settings for file based caching
	private File cacheDirectory;
	private boolean useCache = false;
	private Lang cacheFileLanguage = Lang.TURTLE;
	private String cacheFileExtension = cacheFileLanguage.getFileExtensions().get(0);

	public URIDereferencer(File cacheDirectory) {
		this.cacheDirectory = cacheDirectory;
		this.useCache = cacheDirectory != null;
	}
	
	public URIDereferencer() {
		this(null);
	}
	
	/**
	 * Get the triples that describe the entity identified by the URI.
	 * @param uri the URI of the entity
	 * @return JENA model containing the triples
	 * @throws DereferencingFailedException
	 */
	public Model dereference(String uri) throws DereferencingFailedException{
		return dereference(URI.create(uri));
	}
	
	/**
	 * Get the triples that describe the entity identified by the URI.
	 * @param uri the URI of the entity
	 * @return JENA model containing the triples
	 * @throws DereferencingFailedException
	 */
	public Model dereference(URI uri) throws DereferencingFailedException{
		logger.debug("Dereferencing " + uri + "...");
		Model model = null;
		
		//check if already cached
		if(useCache()){
			model = loadFromDisk(uri);
			return model;
		}
		
		// if we got nothing from cache
		if (model == null) {
			model = ModelFactory.createDefaultModel();
			try {
				URLConnection conn = uri.toURL().openConnection();
				conn.setRequestProperty("Accept", contentType);

				InputStream is = conn.getInputStream();
				model.read(is, null, RDFLanguages.contentTypeToLang(contentType).getLabel());
				is.close();

				if (useCache()) {
					writeToDisk(uri, model);
				}
			} catch (IOException e) {
				throw new DereferencingFailedException(uri, e);
			}
		}
		
    	logger.debug("Done. Got " + model.size() + " triples for " + uri);
    	return model;
	}
	
	/**
	 * Whether to use a file based caching solution, i.e. for each URI the
	 * result is stored in a separate file on disk.
	 * @param useCache use cache or not
	 */
	public void setUseCache(boolean useCache) {
		this.useCache = useCache;
	}
	
	private boolean useCache(){
		return useCache && cacheDirectory != null;
	}
	
	private File getCacheFile(URI uri){
		String filename = UrlEscapers.urlPathSegmentEscaper().escape(uri.toString()) + "." + cacheFileExtension;
    	File cacheFile = new File(cacheDirectory, filename);
    	return cacheFile;
	}
	
	private Model loadFromDisk(URI uri){
		Model model = ModelFactory.createDefaultModel();
		
    	File cachedFile = getCacheFile(uri);
    	if(cachedFile.exists()){
    		try(InputStream is = new BufferedInputStream(new FileInputStream(cachedFile))){
    			model.read(is, null, cacheFileLanguage.getLabel());
    		} catch (IOException e) {
				logger.error("Failed loading from disk.", e);
			}
    		
    	}
    	return model;
	}
	
	private void writeToDisk(URI uri, Model model){
		File cacheFile = getCacheFile(uri);
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(cacheFile))) {
			model.write(os, cacheFileLanguage.getLabel());
		} catch (IOException e) {
			logger.error("Could not write to disk.", e);
		}
	}
	
	class DereferencingFailedException extends Exception{
		
		public DereferencingFailedException(URI uri, Exception cause) {
			super("Dereferencing " + uri + " failed.", cause);
		}
	}
}
