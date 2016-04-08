package eu.eumssi.api.uima;

import java.net.UnknownHostException;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.eumssi.api.json.uima.JSONMeta;
import eu.eumssi.api.json.uima.JSONResponse;
import eu.eumssi.managers.uima.EumssiException;
import eu.eumssi.managers.uima.UimaManager;

/**
 * Analyze text and return results in JSON format
 * 
 * @author jens.grivolla
 * 
 */
@Path("/analyze")
public class Analyze {
	
	/**
	 * Logger for this class and subclasses.
	 */
	protected final Log log = LogFactory.getLog(getClass());

	@Context
	ServletConfig config;

	private UimaManager uimaManager;

	public Analyze() throws UnknownHostException, EumssiException {
		this.uimaManager = UimaManager.getInstance();
	}

	
	@GET
	@Produces("application/json; charset=utf-8")
	public Response analyzeGET(
			@QueryParam("text") String text) {
		return analyzePOST(text);
	}
	
	
	/**
	 * Analyze text
	 * Invalid items are skipped without error message, check the item count
	 * 
	 * @param text (required): plain text to process
	 * 
	 * @return Returns status message and analysis results
	 * 
	 * <br><br>JSON Format for "data":<br>
	 * <code>
	 * {
     *      <id_1>:<analysis results in process-specific JSON format>,
     *      <id_2>: ...
     * }
	 * </code>
	 * 
	 *  <br><br>List of returned status type errors.
	 *  <br>
	 *  <br><code>StatusType.ERROR_UNKNOWN</code> (Error 999) if an unhandled exception is thrown.
	 *
	 */
	@POST
	@Produces("application/json; charset=utf-8")
	public Response analyzePOST(
			@FormParam("text") String text) {
		try {
			
			// check undefined params.
			if (isNull(text) == true) {
				return new JSONResponse(JSONMeta.StatusType.ERROR).toResponse();
			}
			
			// get analysis results
			Map<String, Object> data = uimaManager.analyze(text);
			
			// build JSONResponse
			JSONMeta meta = new JSONMeta(JSONMeta.StatusType.SUCCESS, "analyzed successfully");
			JSONResponse response = new JSONResponse(meta,data);
			return response.toResponse();
		} catch (EumssiException e) {
			return new JSONResponse(e.getStatusType()).toResponse();
		} catch (Exception e) {
			log.error("Unknown exception", e);
			return new JSONResponse(JSONMeta.StatusType.ERROR_UNKNOWN).toResponse();
		}

	}

	static private boolean isNull(Object... objects) {
		for (Object o : objects) {
			if (o == null || o.equals(new String("")))
				return true;
		}
		return false;
	}
		

}