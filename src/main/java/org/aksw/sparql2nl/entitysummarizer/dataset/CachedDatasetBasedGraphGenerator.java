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
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * @author Lorenz Buehmann
 *
 */
public class CachedDatasetBasedGraphGenerator extends DatasetBasedGraphGenerator{
	
	private static final Logger logger = Logger.getLogger(CachedDatasetBasedGraphGenerator.class.getName());
	
	LoadingCache<Configuration, WeightedGraph> graphs = CacheBuilder.newBuilder()
		       .maximumSize(1000)
		       .build(
		           new CacheLoader<Configuration, WeightedGraph>() {
		             public WeightedGraph load(Configuration key) {
		               return buildGraph(key);
		             }
		           });
	
	public File graphsFolder = new File("graphs");
	
	private final HashFunction hf = Hashing.md5();
	private boolean useCache = true;

	/**
	 * @param endpoint
	 * @param cacheDirectory
	 */
	public CachedDatasetBasedGraphGenerator(SparqlEndpoint endpoint, File cacheDirectory) {
		super(endpoint, cacheDirectory);
		
		graphsFolder = new File(cacheDirectory, graphsFolder.getName());
		graphsFolder.mkdirs();
	}
	
	/**
	 * @param endpoint
	 * @param cacheDirectory
	 */
	public CachedDatasetBasedGraphGenerator(SparqlEndpoint endpoint, String cacheDirectory) {
		this(endpoint, new File(cacheDirectory));
	}
	
	public CachedDatasetBasedGraphGenerator(QueryExecutionFactory qef, File cacheDirectory) {
		super(qef);
		
		graphsFolder = new File(cacheDirectory, graphsFolder.getName());
		graphsFolder.mkdirs();
	}
	
	public CachedDatasetBasedGraphGenerator(QueryExecutionFactory qef, String cacheDirectory) {
		this(qef, new File(cacheDirectory));
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
		try {
			return graphs.get(new Configuration(cls, threshold, namespace, c));
		} catch (ExecutionException e) {
			logger.error(e, e);
		}
		return null;
	}
	
	private WeightedGraph buildGraph(Configuration configuration){
		logger.info("Generating graph for " + configuration.cls + "...");
		HashCode hc = hf.newHasher()
		       .putString(configuration.cls.getName(), Charsets.UTF_8)
		       .putDouble(configuration.threshold)
		       .putString(configuration.c.name(), Charsets.UTF_8)
		       .hash();
		String filename = hc.toString() + ".graph";
		File file = new File(graphsFolder, filename);
		WeightedGraph g = null;
		if(useCache && file.exists()){
			logger.info("...loading from disk...");
			try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))){
				g = (WeightedGraph) ois.readObject();
				
				Set<ObjectProperty> outgoingProperties = new HashSet<ObjectProperty>();
				for (Node node : g.getNodes().keySet()) {
					if(node.outgoing){
						outgoingProperties.add(new ObjectProperty(node.label));
					}
				}
				class2OutgoingProperties.put(configuration.cls, outgoingProperties );
			} catch (FileNotFoundException e) {
				logger.error(e, e);
			} catch (IOException e) {
				logger.error(e, e);
			} catch (ClassNotFoundException e) {
				logger.error(e, e);
			}
		} else {
			g = super.generateGraph(configuration.cls, configuration.threshold, configuration.namespace, configuration.c);
			if(useCache){
				try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))){
					oos.writeObject(g);
				} catch (FileNotFoundException e) {
					logger.error(e, e);
				} catch (IOException e) {
					logger.error(e, e);
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
	
	class Configuration{
		NamedClass cls;
		double threshold;
		String namespace;
		Cooccurrence c;
		
		public Configuration(NamedClass cls, double threshold, String namespace, Cooccurrence c) {
			this.cls = cls;
			this.threshold = threshold;
			this.namespace = namespace;
			this.c = c;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((c == null) ? 0 : c.hashCode());
			result = prime * result + ((cls == null) ? 0 : cls.hashCode());
			result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
			long temp;
			temp = Double.doubleToLongBits(threshold);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Configuration other = (Configuration) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (c != other.c)
				return false;
			if (cls == null) {
				if (other.cls != null)
					return false;
			} else if (!cls.equals(other.cls))
				return false;
			if (namespace == null) {
				if (other.namespace != null)
					return false;
			} else if (!namespace.equals(other.namespace))
				return false;
			if (Double.doubleToLongBits(threshold) != Double.doubleToLongBits(other.threshold))
				return false;
			return true;
		}

		private CachedDatasetBasedGraphGenerator getOuterType() {
			return CachedDatasetBasedGraphGenerator.this;
		}
		
		
	}

}
