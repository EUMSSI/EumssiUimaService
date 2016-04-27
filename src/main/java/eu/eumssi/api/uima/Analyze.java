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
		
	/** main method for debugging, runs the service with a standard text.
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Analyze ta = new Analyze();
		//Response res = ta.analyzePOST("Obama visits Merkel in Berlin.");
		Response res = ta.analyzePOST("A hacker group has stolen some 10 million credit cards, putting itself in a position to score US$400 million (£279 million, A$516 million) by infecting 2000 payment terminals with the Trinity point of sales malware. Security firm FireEye and subsidiaries iSIGHT Partners and Mandiant examined the Fin6 group last year after it was found plundering millions of cards.  he first two firms now say the cards stolen from hospitality and retails firms have earned the hacking group hundreds of millions of dollars with each card sold for an average of US$21 on secret popular carder shops. The criminals have sold filched cards since 2014 and have ramped up the cash-out as the value of the stolen cards drops as the United States adopts EMV credit card security."
				+ "The crooks use valid user credentials to gain access, after which modules of the popular Metasploit framework are used to maintain a foothold and set up links with command and control servers to execute shellcode. From there the group followed the advanced-persistent threat cookbook and used various tools to gain privilege escalation and pivot to sensitive areas of the targeted network. Tools exploited three dusty patched vulnerabilities (CVE-2013-3660, CVE-2011-2005, and CVE-2010-4398) that turn local users into kernel-level gods, while the PsExec Metasploit module allows the Active Directory database (ntds.dit) to be swiped and password hashes cracked online. In one day, so write the researchers, Fin6 flayed 900 SQL servers gaining intel information to support further hacking operations. The exfiltration of point of sales data once collected through Trinity took a few more steps, as follows:"
				+ "FIN6 used a script to systematically iterate through a list of compromised POS systems, copying the harvested track data files to a numbered log file before removing the original data files. They then compressed the log files into a ZIP archive and moved the archive through the environment to an intermediary system and then to a staging system. From the staging system, they then copied the stolen data to external command and control servers under their control using the FTP command line utility.");
		System.out.print(res.toString());
		System.out.print(res.getEntity().toString());
	}

}