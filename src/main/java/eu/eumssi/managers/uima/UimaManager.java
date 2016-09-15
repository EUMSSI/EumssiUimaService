package eu.eumssi.managers.uima;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.dbpedia.spotlight.uima.SpotlightAnnotator;
import org.dbpedia.spotlight.uima.types.DBpediaResource;
import org.dbpedia.spotlight.uima.types.TopDBpediaResource;

import com.iai.uima.analysis_component.KeyPhraseAnnotator;
import com.iai.uima.jcas.tcas.KeyPhraseAnnotation;
import com.iai.uima.jcas.tcas.KeyPhraseAnnotationDeprecated;

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.languagetool.LanguageToolSegmenter;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordNamedEntityRecognizer;
import edu.upf.glicom.uima.ae.ConfirmLinkAnnotator;
import eu.eumssi.api.json.uima.JSONMeta.StatusType;

import org.apache.solr.client.solrj.util.ClientUtils;

/**
 * This class represents the QueryManager component which interfaces with the backend MongoDB.
 * 
 * @author jens.grivolla
 *
 */
public class UimaManager {

	/**
	 * Logger for this class and subclasses.
	 */
	protected final Log log = LogFactory.getLog(getClass());

	/**
	 * Properties files
	 */
	private static final String PROPERTIES_FILE = "/eu/eumssi/properties/uima.properties";

	/**
	 * Singleton instance of QueryManager.
	 */
	private static UimaManager instance;

	/**
	 * Configuration properties.
	 */
	private Properties properties;

	private AnalysisEngine ae;

	private String dbpediaService;


	/**
	 * Return a unique instance of QueryManager (Singleton pattern).
	 * @return a unique instance of QueryManager
	 * @throws UnknownHostException 
	 * @throws EumssiException 
	 */
	public static UimaManager getInstance() throws UnknownHostException, EumssiException {
		if (instance == null) {
			instance = new UimaManager();
		}
		return instance;
	}

	/**
	 * Private constructor (Singleton pattern)
	 * @throws UnknownHostException 
	 * @throws EumssiException 
	 */
	private UimaManager() throws EumssiException{
		try {
			BasicConfigurator.configure(); // ugly hack to get it working, should use properties file instead
			loadProperties();
			this.dbpediaService = this.properties.getProperty("dbpediaUrl");
			log.info("set dbpediaUrl to "+this.dbpediaService);
		} catch (Exception e) {
			log.error("Error loading properties file", e);
			throw new EumssiException(StatusType.ERROR);
		}
		try {
			setupPipeline();
		} catch (ResourceInitializationException e) {
			log.error("Error configuring UIMA pipeline", e);
			throw new EumssiException(StatusType.ERROR_UNKNOWN);
		}
	}

	private void setupPipeline() throws ResourceInitializationException {
		AnalysisEngineDescription segmenter = createEngineDescription(LanguageToolSegmenter.class);

		AnalysisEngineDescription dbpedia = createEngineDescription(SpotlightAnnotator.class,
				//SpotlightAnnotator.PARAM_ENDPOINT, "http://localhost:2222/rest",
				SpotlightAnnotator.PARAM_ENDPOINT, this.dbpediaService,
				SpotlightAnnotator.PARAM_CONFIDENCE, 0.35f,
				SpotlightAnnotator.PARAM_ALL_CANDIDATES, false);

		AnalysisEngineDescription ner = createEngineDescription(StanfordNamedEntityRecognizer.class);

		AnalysisEngineDescription validate = createEngineDescription(ConfirmLinkAnnotator.class);

		AnalysisEngineDescription key = createEngineDescription(KeyPhraseAnnotator.class,
				KeyPhraseAnnotator.PARAM_LANGUAGE, "en",
				KeyPhraseAnnotator.PARAM_KEYPHRASE_RATIO, 80
				);

		//this.ae = createEngine(createEngineDescription(segmenter, dbpedia, ner, validate));
		this.ae = createEngine(createEngineDescription(segmenter, dbpedia, ner, validate, key));
	}

	/**
	 * Load the QueryManager properties file.
	 * 
	 * @return
	 * @throws IOException
	 */
	private boolean loadProperties() throws IOException
	{
		this.properties = new Properties();
		InputStream in = this.getClass().getResourceAsStream(PROPERTIES_FILE);
		this.properties.load(in);
		in.close();
		return true;		
	}

	/** convert DBpedia resource types to Stanford NER types
	 * @param types space separated list of DBpedia types
	 * @return set of matching NER types
	 */
	private static Set<String> convertTypes(String types) {
		Set<String> typeSet = new HashSet<String>();
		if (types.matches("(PERSON)|(I-PER)|(.*Person.*)")) typeSet.add("PERSON");
		if (types.matches("(LOCATION)|(I-LOC)|(.*Place.*)")) 
			typeSet.add("LOCATION");
		if (types.matches("(ORGANIZATION)|(I-ORG)|(.*Organisation.*)")) typeSet.add("ORGANIZATION");
		if (types.matches("(MISC)|(I-MISC)")) typeSet.add("MISC");
		if (types.matches(".*City.*"))
			typeSet.add("City");
		if (types.matches(".*Country.*")) typeSet.add("Country");
		if (typeSet.isEmpty()) {
			typeSet.add("other");
		}
		return typeSet;
	}

	/** adds entities/resources to the entityMap structure according to the entity type
	 * @param entityMap MongoDB structure to be filled
	 * @param type type of the entity to add
	 * @param entity the entity name/URI
	 */
	@SuppressWarnings("unchecked")
	private static void addWithType(Map<String,Object> entityMap, String type, Map<String,Object> entity) {
		List<Object> entityList = null;
		// create field for each entity type
		if (entityMap.containsKey(type)) {
			entityList = (List<Object>) entityMap.get(type);
		} else {
			entityList = new ArrayList<Object>();
			entityMap.put(type, entityList);
		}
		entityList.add(entity);
	}

	/**
	 * analyzes a given text
	 * @param text the text to analyze
	 * @return
	 * @throws EumssiException
	 */
	public Map<String, Object> analyze(String text) throws EumssiException  {
		Map<String, Object> analysisResult = new HashMap<String,Object>();
		JCas jCas;
		try {
			jCas = JCasFactory.createText(text);
			jCas.setDocumentLanguage("en");
			this.ae.process(jCas);

			List<String> dbpediaUris = new ArrayList<String>();
			
			Map<String, Object> dbpediaMap = new HashMap<String,Object>();
			for (DBpediaResource resource : select(jCas, TopDBpediaResource.class)) {
				// multiword or contains upper case
				if (resource.getCoveredText().contains(" ") || !resource.getCoveredText().equals(resource.getCoveredText().toLowerCase())) {
					Map<String, Object> res = new HashMap<String,Object>();
					res.put("text", resource.getCoveredText());
					res.put("uri", resource.getUri());
					res.put("type", resource.getTypes());
					res.put("begin", resource.getBegin());
					res.put("end", resource.getEnd());
					for (String type : convertTypes((String) res.get("type"))) {
						addWithType(dbpediaMap, type, res);
					}
					addWithType(dbpediaMap, "all", res);
					dbpediaUris.add(ClientUtils.escapeQueryChars(resource.getUri()));
				}
			}
			analysisResult.put("dbpedia", dbpediaMap);

			List<String> stanfordEntities = new ArrayList<String>();

			Map<String, Object> stanfordMap = new HashMap<String,Object>();
			for (NamedEntity entity : select(jCas, NamedEntity.class)) {
				Map<String, Object> res = new HashMap<String,Object>();
				res.put("text", entity.getCoveredText());
				res.put("type", entity.getValue());
				res.put("begin", entity.getBegin());
				res.put("end", entity.getEnd());
				for (String type : convertTypes((String) res.get("type"))) {
					addWithType(stanfordMap, type, res);
				}
				addWithType(stanfordMap, "all", res);
				stanfordEntities.add(ClientUtils.escapeQueryChars(entity.getCoveredText()));
			}
			analysisResult.put("stanford", stanfordMap);

			ArrayList<Map<String, Object>> keaList = new ArrayList<Map<String, Object>>();
			for (KeyPhraseAnnotation entity : select(jCas, KeyPhraseAnnotation.class)) {
				if (!(entity instanceof KeyPhraseAnnotationDeprecated)) {
					Map<String, Object> res = new HashMap<String,Object>();
					res.put("text", entity.getCoveredText());
					res.put("keyphrase", entity.getKeyPhrase());
					res.put("stemmed", entity.getStem());
					res.put("rank", entity.getRank());
					res.put("probability", entity.getProbability());
					res.put("begin", entity.getBegin());
					res.put("end", entity.getEnd());
					keaList.add(res);
				}
			}
			keaList.sort(Comparator.comparingInt((Map<String, Object> k) -> (Integer) k.get("rank")));
			analysisResult.put("kea", keaList);

			String solrSimilarity = "";
			
			if (dbpediaUris.size() > 0) {
				solrSimilarity += "meta.extracted.text_nerl.dbpedia.all:(" + String.join(" ", dbpediaUris) + ")";
			}
			if (stanfordEntities.size() > 0) {
				solrSimilarity += " meta.extracted.text_nerl.ner.all:(" + String.join(" ", stanfordEntities) + ")";
			}
			HashMap<String, String> solrQueries = new HashMap<String,String>();
			solrQueries.put("similarity", solrSimilarity);
			analysisResult.put("solr", solrQueries);

			return analysisResult;
		} catch (UIMAException e) {
			log.error("Error processing document", e);
			throw new EumssiException(StatusType.ERROR_UNKNOWN);
		}
	}

}
