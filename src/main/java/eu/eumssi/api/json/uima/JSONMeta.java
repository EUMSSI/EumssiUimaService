package eu.eumssi.api.json.uima;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This class represents a Meta message.
 * It contains information about every REST API method calling, such as
 * code number, status and message.
 * 
 * @author jens.grivolla
 */
public class JSONMeta {
	
	/**
	 * JSON converter
	 */
	private static Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
	
	/**
	 * Enumeration of status types 
	 */
	public enum StatusType 
	{
		SUCCESS,
		ERROR,
		ERROR_UNKNOWN,
	}
	
	private static final Map<StatusType, String> statusMessages;
	
	private static final Map<StatusType, Integer> statusCodes;
	
	static 
	{
		statusMessages = new HashMap<StatusType, String>();
		statusMessages.put(StatusType.SUCCESS, "Success");
		statusMessages.put(StatusType.ERROR, "Error");
		statusMessages.put(StatusType.ERROR_UNKNOWN, "Unknown error");
		
		statusCodes = new HashMap<StatusType, Integer>();
		statusCodes.put(StatusType.SUCCESS, 0);
		statusCodes.put(StatusType.ERROR, 1);
		statusCodes.put(StatusType.ERROR_UNKNOWN, 999);
		
	}
	
	
	@SuppressWarnings("unused")
	private String message = "";
	@SuppressWarnings("unused")
	private String code = "";
	private String status = "";
	
	/**
	 * Default constructor
	 * @param statusType Type of status (Ok, Error, ...)
	 */
	public JSONMeta(StatusType statusType)
	{
		this.message = statusMessages.get(statusType);
		this.code = Integer.toString(statusCodes.get(statusType));
		this.status = "error";
		if (statusType == StatusType.SUCCESS)
			this.status = "ok";		
	}
	
	/**
	 * 
	 * @param statusType Type of status (Ok, Error, ...)
	 */
	public JSONMeta(StatusType statusType, String message)
	{
		this.message = message;
		this.code = Integer.toString(statusCodes.get(statusType));
		this.status = "error";
		if (statusType == StatusType.SUCCESS)
			this.status = "ok";		
	}
	
			
	public String getStatus() {
		return status;
	}

	/**
	 * Converts the object to a JSON representation
	 * @return A String in JSON format of itself
	 */
	public String toJson()
	{
		return JSONMeta.gson.toJson(this, this.getClass());			
	}
	
	
	public static void printErrors() {
		for (StatusType key : StatusType.values())	{
			int code = statusCodes.get(key);
			String message = statusMessages.get(key);
			System.out.println("Error " + code + ": " + message);
		}
	}
	
}