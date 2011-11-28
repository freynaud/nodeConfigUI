package org.openqa.grid.selenium;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.selenium.services.BrowserFinderUtils;
import org.openqa.grid.selenium.services.FileSystemService;
import org.openqa.grid.selenium.services.HubUtils;
import org.openqa.grid.selenium.services.WebDriverValidationService;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.google.common.io.ByteStreams;

/**
 * front end for the node.
 * 
 * @author freynaud
 * 
 */
public class NodeConfigServlet extends HttpServlet {

	private static final long serialVersionUID = 7490344466454529896L;
	private Node node;
	private FileSystemService service = new FileSystemService();
	private BrowserFinderUtils browserUtils = new BrowserFinderUtils();
	private WebDriverValidationService wdValidator = new WebDriverValidationService();

	// fixed as the page itself is used as part of the node validation.
	public final static String PAGE_TITLE = "WebDriver node config";

	// the page
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (node == null) {
			int port = request.getServerPort();
			node = new Node(port);
		}
		String page = getPage();
		write(page, response);
	}
	
	private void write(String content, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		response.setStatus(200);

		InputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
		try {
			ByteStreams.copy(in, response.getOutputStream());
		} finally {
			in.close();
			response.getOutputStream().close();
		}
	}
	
	

	// the ajax requests
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (node == null) {
			int port = request.getServerPort();
			node = new Node(port);
		}
		try {
			JSONObject ajax = processAjax(request, response);
			write(ajax.toString(), response);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * ajax request linked to the page. Convention is {"A":"value a"} will
	 * result in $(#A) content will be updated with value a using JQuery
	 * locators.
	 * 
	 * @param request
	 * @param response
	 * @return a JSONObject representing the page element to updatz
	 * @throws JSONException
	 * @throws IOException
	 */
	private JSONObject processAjax(HttpServletRequest request, HttpServletResponse response) throws JSONException, IOException {

		String status = request.getParameter("status");
		if (status != null) {
			String url = request.getParameter("url");
			return pingHub(url);
		}

		String reset = request.getParameter("reset");
		if (reset != null) {
			return loadDefault();
		}

		String update = request.getParameter("update");
		if (update != null) {
			return updateNode(request);
		}

		String delete = request.getParameter("remove");
		if (delete != null) {
			return removeCapability(delete);
		}

		String typed = request.getParameter("completion");
		if (typed != null) {
			return service.complete(typed);
		}

		String proposedPath = request.getParameter("submit");
		if (proposedPath != null) {
			return seekBrowsers(proposedPath);
		}

		String load = request.getParameter("load");
		if (load != null) {
			return loadFromFile();
		}
		String save = request.getParameter("save");
		if (save != null) {
			return saveToFile();
		}

		String current = request.getParameter("current");
		if (current != null) {
			JSONObject o = service.seemsValid(current);
			return o;
		}
		String index = request.getParameter("validate");
		if (index != null) {
			return valideCapability(index);
		}

		String refreshubFB = request.getParameter("refreshubFB");
		if (refreshubFB != null) {
			return refreshubFB();
		}

		String register = request.getParameter("register");
		if (register != null) {
			return register();
		}

		return null;
	}

	/**
	 * try to guess the type.If it looks like an int, return an int.
	 * 
	 * @param capValue
	 * @return
	 */
	private Object cast(String capValue) {
		try {
			return Integer.parseInt(capValue);
		} catch (Throwable e) {
			return capValue;
		}
	}

	

	/**
	 * get the content of the html page
	 * 
	 * @return
	 */
	private String getPage() {
		StringBuilder builder = new StringBuilder();
		builder.append("<html>");

		builder.append("<head>");
		builder.append("<script src='http://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js'></script>");
		builder.append("<script src='resources/NodeConfig.js'></script>");
		builder.append("<link rel='stylesheet' type='text/css' href='resources/NodeConfig.css' />");
		builder.append("<title>" + PAGE_TITLE + "</title>");
		builder.append("</head>");

		builder.append("<body>");

		builder.append("<div class='error' >");
		for (String browser : node.getErrorPerBrowser().keySet()) {
			builder.append(browser + " : " + node.getErrorPerBrowser().get(browser) + "</br>");
		}
		builder.append("</div>");

		// Platform
		Icon os = null;
		switch (node.getPlatform()) {
		case LINUX:
			os = Icon.LINUX;
			break;
		case MAC:
			os = Icon.MAC;
			break;
		case WINDOWS:
		case VISTA:
		case XP :
			os = Icon.WIN;
			break;
		default:
			break;
		}
		builder.append("<b>Platform :</b> <img  src='"+os.path()+ "' title='" + node.getPlatform() + "'  ></br></br>");

		// hub
		builder.append("<b>Part of the grid : </b>");
		builder.append(" <input id='hub_url' size='40' value='http://localhost:4444' >");
		builder.append("<img ' id='hub_satus_icon' src='" + Icon.NOT_SURE.path() + "' >");

		builder.append("<div id ='hubInfo' ><i>edit the url to point to another hub.</i></div></br>");

		// connectivity
		builder.append("<b>Currently <span id='proxyState' ></span> </b>");
		builder.append("<a href='#' id='register' > register </a></br></br>");
		builder.append("Feedback from hub :<div id='hubFB'></div><a id='refreshubFB' href='#' >get feedback</a>");
		// capabilities
		builder.append(getCapabilitiesDiv());

		builder.append("</div></br>");

		// discover more browsers manually
		builder.append("<b>Add more capabilities :</b></br>");
		builder.append("<input id='browserLocation' size='50' >");
		builder.append("<div id='seekBrowserFB' class='autoHide'></div>");
		builder.append("<div id='completionFB' class='autoHide' ></div></br>");

		// configuration
		builder.append("<b>Configuration:</b></br>");
		builder.append("<div id='configuration' >");
		builder.append(getConfigurationDiv());
		builder.append("</div>");

		// save / load.
		builder.append("<b>Backup:</b> (");
		builder.append("<span id='backupFile'  >" + node.getBackupFile().getAbsolutePath() + ")</span></br>");
		builder.append("<a id='load' href='#' >load</a></br>");
		builder.append("<div id='loadFB' class='autoHide' ></div>");
		builder.append("<a id='save' href='#' >save</a></br>");
		builder.append("<div id='saveFB' class='autoHide' ></div>");
		builder.append("<a id='reset' href='#' >reset</br></a>");
		builder.append("<div id='resetFB' ></div>");

		builder.append("<div id='json' >" + getJSONContent() + "</div>");

		builder.append("</body>");
		builder.append("</html>");

		return builder.toString();

	}

	// basic formating
	private String getJSONContent() {
		try {
			JSONObject o = node.getJSON();
			JSONArray capabilities = o.getJSONArray("capabilities");
			JSONObject configuration = o.getJSONObject("configuration");
			StringBuilder b = new StringBuilder();
			b.append("{\n");
			b.append("\"capabilities\":\n");
			b.append("\t[\n");
			for (int i = 0; i < capabilities.length(); i++) {
				JSONObject cap = capabilities.getJSONObject(i);
				b.append("\t\t{\n");
				for (Iterator iterator = cap.keys(); iterator.hasNext();) {
					String key = (String) iterator.next();
					b.append("\t\t\t\"" + key + "\" : ");
					Object v = cap.get(key);
					if (v instanceof Boolean || v instanceof Integer) {
						b.append(v);
					} else {
						b.append("\"" + v + "\"");
					}

					b.append("\n");
				}

				b.append("\t\t},\n");
			}
			b.append("\t],\n");

			b.append("\"configuration\":\n");
			b.append("\t{\n");

			for (Iterator iterator = configuration.keys(); iterator.hasNext();) {
				String key = (String) iterator.next();
				b.append("\t\t\"" + key + "\" : ");
				Object v = configuration.get(key);
				if (v instanceof Boolean || v instanceof Integer) {
					b.append(v);
				} else {
					b.append("\"" + v + "\"");
				}

				b.append("\n");
			}

			b.append("\t}\n");
			b.append("}\n");
			return "<pre>" + b.toString() + "</pre>";
		} catch (JSONException js) {
			return "jspn parsing error " + js.getMessage();
		}
	}

	private String getCapabilitiesDiv() {
		StringBuilder builder = new StringBuilder();
		builder.append("<div id='capabilities'>");

		builder.append("<b>Discovered capabilities :</b></br>");
		builder.append("<table border='2'>");
		builder.append("<tr>");
		builder.append("<td width='90px'>Status</td>");
		builder.append("<td width='90px'>Browser</td>");
		builder.append("<td width='90px' >Instances</td>");
		builder.append("<td width='100px' >Version</td>");
		builder.append("<td>Binary</td>");
		builder.append("</tr>");

		int i = 0;
		for (DesiredCapabilities capability : node.getCapabilities()) {

			int index = node.getCapabilities().indexOf(capability);

			builder.append("<tr id='capability_" + index + "'>");

			// status
			String status;
			String iconStatus = Icon.NOT_SURE.path();
			String clazz = "";
			String valid = (String) capability.getCapability("valid");
			if ("running".equals(valid)) {
				iconStatus = Icon.NOT_SURE.path();
				status = "running a test";
			} else if ("true".equals(valid)) {
				iconStatus = Icon.CLEAN.path();
				status = "browser ready";
				clazz = "validate_cap";
			} else if ("false".equals(valid)) {
				iconStatus = Icon.ALERT.path();
				clazz = "validate_cap";
				status = "" + capability.getCapability("error");

			} else {
				iconStatus = Icon.NOT_SURE.path();
				clazz = "validate_cap";
				status = "may be working.";
			}

			builder.append("<td>");
			builder.append("<img index='" + index + "' src='" + iconStatus + "' title='" + status + "' class='" + clazz + "' >");
			builder.append("<img index='" + index + "' src='" + Icon.DELETE.path() + "' title='Delete' class='remove' >");
			builder.append("</td>");

			// browser
			builder.append("<td>");
			String browser = capability.getBrowserName();
			//TODO freynaud fix that
			//builder.append("<img src='/extra/resources/" + BrowserNameUtils.consoleIconName(capability) + ".png'  title='" + browser + "'>");
			builder.append("<img src='/extra/resources/" + capability.getBrowserName() + ".png'  title='" + browser + "'>");
			builder.append("</td>");

			// instance
			builder.append("<td>");
			int instances = (Integer) (capability.getCapability(RegistrationRequest.MAX_INSTANCES));
			builder.append("<input  size='2' class='" + RegistrationRequest.MAX_INSTANCES + "' index='" + index + "' value='" + instances + "' />");
			builder.append("</td>");

			// version
			builder.append("<td>");
			builder.append(("".equals(capability.getVersion()) ? "??" : capability.getVersion()));
			builder.append("</td>");

			// binary
			builder.append("<td>");
			if ("firefox".equals(browser)) {
				builder.append(capability.getCapability(FirefoxDriver.BINARY));
			} else if ("chrome".equals(browser)) {
				builder.append(capability.getCapability("chrome.binary"));
			} else if ("opera".equals(browser)) {
				builder.append(capability.getCapability("opera.binary"));
			}
			builder.append("<td>");

			builder.append("</tr>");

			i++;
		}
		builder.append("</table>");
		return builder.toString();
	}

	private String getConfigurationDiv() {
		StringBuilder builder = new StringBuilder();
		builder.append("<ul>");
		for (String key : node.getConfiguration().keySet()) {
			builder.append("<li>");
			builder.append("<b>" + key + "</b> : ");
			builder.append(node.getConfiguration().get(key));
			builder.append("</li>");
		}
		builder.append("</ul>");
		return builder.toString();
	}

	/*
	 * json stuff
	 */

	/**
	 * try to find as many browser as possible from the given path. TODO
	 * freynaud : Only tested with firefox. does it make sense with other
	 * browsers ?
	 */
	private JSONObject seekBrowsers(String proposedPath) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("success", true);
		o.put("seekBrowserFB", "");

		File f = new File(proposedPath);
		if (!f.exists()) {
			o.put("success", false);
			o.put("seekBrowserFB", f + " is not a valid file.");
			return o;
		} else if (!f.isFile()) {
			o.put("success", false);
			o.put("seekBrowserFB", f + " is a folder.You need to specify a file.");
			return o;
		} else {
			List<String> addeds = new ArrayList<String>();
			List<DesiredCapabilities> founds = browserUtils.findAllInstallsAround(proposedPath);
			for (DesiredCapabilities c : founds) {
				if (node.addNewBrowserInstall(c)) {
					addeds.add(c.getBrowserName() + " v" + c.getVersion());
				}
			}
			if (addeds.isEmpty()) {
				o.put("success", false);
				o.put("seekBrowserFB", "no new browser install found from " + proposedPath);
				return o;
			} else {

				String c = "Woot." + addeds.size() + " new browsers found</br>";
				for (String s : addeds) {
					c += s + "<br>";
				}
				o.put("seekBrowserFB", c);
				o.put("capabilities", getCapabilitiesDiv());
				o.put("json", getJSONContent());
				return o;
			}
		}
	}

	/**
	 * check the capability with index index in the node capabilities list, and
	 * try to run a simple test on it, to validate that everything is fine. Will
	 * try to get more info like the version too.
	 * 
	 * @param index
	 * @return
	 * @throws JSONException
	 */
	private JSONObject valideCapability(String index) throws JSONException {
		int i = Integer.parseInt(index);
		DesiredCapabilities c = node.getCapabilities().get(i);
		JSONObject o = new JSONObject();
		try {
			c.setCapability("valid", "running");
			DesiredCapabilities realCap = wdValidator.validate(node.getPort(), c);
			o.put("success", true);
			BrowserFinderUtils.updateGuessedCapability(c, realCap);
			o.put("info", "Success !");
			c.setCapability("valid", "true");
		} catch (GridException e) {
			o.put("success", false);
			c.setCapability("valid", "false");
			c.setCapability("error", e.getMessage());
			o.put("info", e.getMessage());

		}
		o.put("capabilities", getCapabilitiesDiv());
		o.put("configuration", getConfigurationDiv());
		o.put("json", getJSONContent());
		return o;
	}

	/**
	 * save the node description to the underlying json config file
	 * 
	 * @return
	 * @throws JSONException
	 */
	private JSONObject saveToFile() throws JSONException {
		JSONObject o = new JSONObject();
		try {
			node.save();
			o.put("success", true);
			o.put("saveFB", "Great success! Config saved in " + node.getBackupFile().getAbsolutePath());
			return o;
		} catch (IOException e) {
			o.put("success", false);
			o.put("saveFB", ":( " + e.getMessage());
			return o;
		}
	}

	/**
	 * load from the underlying config file
	 * 
	 * @return
	 * @throws JSONException
	 */
	private JSONObject loadFromFile() throws JSONException {
		JSONObject o = new JSONObject();
		try {
			node.load();
			o.put("success", true);
			o.put("loadFB", "Great success!");
			o.put("capabilities", getCapabilitiesDiv());
			o.put("configuration", getConfigurationDiv());
			o.put("json", getJSONContent());

			return o;
		} catch (IOException e) {
			o.put("success", false);
			o.put("loadFB", ":( " + e.getMessage());
			return o;
		}
	}

	/**
	 * update something on the node with the following convention :
	 * capabilities.index.key=value configuration.key=value
	 * 
	 * @param request
	 * @return
	 * @throws JSONException
	 */
	private JSONObject updateNode(HttpServletRequest request) throws JSONException {
		for (Enumeration e = request.getParameterNames(); e.hasMoreElements();) {
			String p = (String) e.nextElement();
			if (p.startsWith("capabilities")) {
				String value = request.getParameter(p);
				String[] pieces = p.split("\\.");
				int capIndex = Integer.parseInt(pieces[1]);
				String capKey = pieces[2];
				String capValue = value;
				node.getCapabilities().get(capIndex).setCapability(capKey, cast(capValue));
			} else if (p.startsWith("configuration")) {
				String value = request.getParameter(p);
				String configKey = value.split("\\.")[1];
				String configValue = value;
				node.getConfiguration().put(configKey, cast(configValue));
			}

		}
		JSONObject o = new JSONObject();
		o.put("success", false);
		o.put("capabilities", getCapabilitiesDiv());
		o.put("configuration", getConfigurationDiv());
		o.put("json", getJSONContent());
		return o;
	}

	/**
	 * load the default settings for the current platform.
	 * 
	 * @return
	 * @throws JSONException
	 */
	private JSONObject loadDefault() throws JSONException {
		node.loadDefault();
		JSONObject o = new JSONObject();
		o.put("success", true);
		o.put("resetFB", "");
		o.put("capabilities", getCapabilitiesDiv());
		o.put("configuration", getConfigurationDiv());
		o.put("json", getJSONContent());

		return o;
	}

	/**
	 * check if the hub is up and running.
	 */
	private JSONObject pingHub(String url) throws JSONException {
		JSONObject o = new JSONObject();

		URL hubUrl;
		try {
			hubUrl = new URL(url);
			node.setHubURL(hubUrl);
		} catch (MalformedURLException e) {
			o.put("success", false);
			o.put("hub_satus_icon.src", Icon.ALERT.path());
			o.put("hub_satus_icon.title", url + " is not a valid url");
			o.put("hubInfo", url + " is not a valid url");
			return o;
		}

		HubUtils hubUtils = new HubUtils(node.getHubURL().getHost(), node.getHubURL().getPort());
		boolean ok = hubUtils.isHubReachable();

		o.put("success", ok);
		if (ok) {
			o.put("hub_satus_icon.src", Icon.CLEAN.path());
			o.put("hub_satus_icon.title", "/extra/resources/clean.png");
			o.put("hubInfo", "hub up and waiting for reg request.");
		} else {
			o.put("hub_satus_icon.src", Icon.ALERT.path());
			o.put("hub_satus_icon.title", "Cannot contact " + hubUtils.getUrl());
			o.put("hubInfo", "Cannot contact " + hubUtils.getUrl());
		}
		o.put("configuration", getConfigurationDiv());
		o.put("json", getJSONContent());
		return o;
	}

	/**
	 * delete the capability with index = i on the node
	 * 
	 * @param delete
	 * @return
	 * @throws JSONException
	 */
	private JSONObject removeCapability(String index) throws JSONException {
		int i = Integer.parseInt(index);
		node.getCapabilities().remove(i);
		JSONObject o = new JSONObject();
		o.put("success", true);
		o.put("capabilities", getCapabilitiesDiv());
		o.put("json", getJSONContent());
		return o;

	}

	private JSONObject register() throws IOException, JSONException {
		boolean ok = node.register();
		JSONObject o = new JSONObject();
		o.put("success", true);
		return o;
	}

	private JSONObject refreshubFB() throws JSONException {
		JSONObject o = new JSONObject();
		StringBuilder b = new StringBuilder();
		try {
			
			o.put("success", true);
			JSONObject res = node.getStatusFromHub();
			
			// b.append("<b>original request : </b>"+res.get("request")+"</br>");
			// o.put("hubFB",b.toString() );
			if (res.getBoolean("success")) {
				b.append("registered ");
			} else {
				b.append("not registered ");
				o.put("proxyState", b.toString());
				return o;
			}
			if (res.getBoolean("isDown")) {
				b.append(" and inactive :");
			} else {
				b.append(" and active :");
			}
			o.put("proxyState", b.toString());
			return o;
			
		} catch (Throwable t) {
			o.put("proxyState", "error contacting the hub");
		}
		
		return o;

	}

}
