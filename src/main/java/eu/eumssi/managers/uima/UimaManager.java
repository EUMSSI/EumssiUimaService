package eu.eumssi.managers.uima;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.JCasIterable;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.dbpedia.spotlight.uima.SpotlightAnnotator;
import org.dbpedia.spotlight.uima.types.DBpediaResource;
import org.dbpedia.spotlight.uima.types.TopDBpediaResource;

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.languagetool.LanguageToolSegmenter;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordNamedEntityRecognizer;
import edu.upf.glicom.uima.ae.ConfirmLinkAnnotator;
import edu.upf.glicom.uima.ts.VerifiedDBpediaResource;
import eu.eumssi.api.json.uima.JSONMeta.StatusType;


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

		this.ae = createEngine(createEngineDescription(segmenter, dbpedia, ner, validate));
		//this.ae = createEngine(createEngineDescription(dbpedia));
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

			ArrayList dbpediaList = new ArrayList();
			for (DBpediaResource resource : select(jCas, TopDBpediaResource.class)) {
				Map<String, Object> res = new HashMap<String,Object>();
				res.put("text", resource.getCoveredText());
				res.put("uri", resource.getUri());
				res.put("type", resource.getTypes());
				res.put("begin", resource.getBegin());
				res.put("end", resource.getEnd());
				dbpediaList.add(res);
			}
			analysisResult.put("dbpedia", dbpediaList);

			ArrayList stanfordList = new ArrayList();
			for (NamedEntity entity : select(jCas, NamedEntity.class)) {
				Map<String, Object> res = new HashMap<String,Object>();
				res.put("text", entity.getCoveredText());
				res.put("type", entity.getValue());
				res.put("begin", entity.getBegin());
				res.put("end", entity.getEnd());
				stanfordList.add(res);
			}
			analysisResult.put("stanford", stanfordList);
			return analysisResult;
		} catch (UIMAException e) {
			log.error("Error processing document", e);
			throw new EumssiException(StatusType.ERROR_UNKNOWN);
		}
	}

}
