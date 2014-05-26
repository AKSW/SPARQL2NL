/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.dataset;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * @author Lorenz Buehmann
 *
 */
public class CachedDatasetBasedGraphGenerator extends DatasetBasedGraphGenerator{
	
	private static final Logger logger = Logger.getLogger(CachedDatasetBasedGraphGenerator.class.getName());
	
	public File graphsFolder = new File("cache/graphs");
	
	private final HashFunction hf = Hashing.md5();
	private boolean useCache = true;

	/**
	 * @param endpoint
	 * @param cacheDirectory
	 */
	public CachedDatasetBasedGraphGenerator(SparqlEndpoint endpoint, String cacheDirectory) {
		super(endpoint, cacheDirectory);
		
		graphsFolder.mkdirs();
	}
	
	/**
	 * @param endpoint
	 * @param cache
	 */
	public CachedDatasetBasedGraphGenerator(SparqlEndpoint endpoint, CacheCoreEx cache) {
		super(endpoint, cache);
		
		graphsFolder.mkdirs();
	}
	
	/* (non-Javadoc)
	 * @see org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator#generateGraph(org.dllearner.core.owl.NamedClass, double, java.lang.String, org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence)
	 */
	@Override
	public WeightedGraph generateGraph(NamedClass cls, double threshold, String namespace, Cooccurrence c) {
		logger.info("Generating graph for " + cls + "...");
		HashCode hc = hf.newHasher()
		       .putString(cls.getName(), Charsets.UTF_8)
		       .putDouble(threshold)
		       .putString(c.name())
		       .hash();
		String filename = hc.toString() + ".graph";
		File file = new File(graphsFolder, filename);
		WeightedGraph g = null;
		if(useCache && file.exists()){
			logger.info("...loading from disk...");
			try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))){
				g = (WeightedGraph) ois.readObject();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			g = super.generateGraph(cls, threshold, namespace, c);
			if(useCache){
				try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))){
					oos.writeObject(g);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
		}
		logger.info("...done.");
		return g;
	}
	
	/**
	 * @param useCache the useCache to set
	 */
	public void setUseCache(boolean useCache) {
		this.useCache = useCache;
	}
	
	public void precomputeGraphs(double threshold, String namespace, Cooccurrence c){
		Set<NamedClass> classes = reasoner.getOWLClasses();
		for (NamedClass cls : classes) {
			generateGraph(cls, threshold, namespace, c);
		}
	}

}
