package org.openqa.grid.selenium.services;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.remote.DesiredCapabilities;

/**
 * Helpers to browse the node's hard drive and help discover the browsers.
 * 
 * @author freynaud
 * 
 */
public class FileSystemService {

	/**
	 * take the path and see if that could be a web browser.
	 * 
	 * @param path
	 * @return
	 * @throws JSONException
	 */
	public JSONObject seemsValid(String path) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("success", false);
		o.put("validFB", path + " is not a valid browser executable");

		File f = new File(path);
		if (f.exists() && f.isFile()) {
			o.put("success", true);
			DesiredCapabilities cap = new BrowserFinderUtils().discoverCapability(new File(path));
			if (cap != null) {
				o.put("validFB", path + " appear to be a valid " + cap.getBrowserName() + " v" + cap.getVersion() + " install.");
			} else {
				throw new RuntimeException("doesn't look like a browser");
			}

		}
		return o;

	}

	/**
	 * helper for path completion.Does what tab would do on a unix like machine.
	 */
	public JSONObject complete(String typed) throws JSONException {

		JSONObject o = new JSONObject();
		o.put("success", true);
		o.put("browserLocation", typed);
		o.put("completionFB", "");

		String sep = System.getProperty("file.separator");
		String splitter = sep;
		if ("\\".equals(sep)){
			splitter = "\\\\";
		}
		String[] pieces = typed.split(splitter);
		if (pieces.length == 0) {
			o.put("success", false);
			o.put("completionFB", typed + " doesn't look like a valid path.");
			return o;
		}

		StringBuilder b = new StringBuilder();
		b.append(sep);

		for (int i = 0; i < (pieces.length - 1); i++) {
			b.append(pieces[i]).append(sep);
		}

		File folder = new File(b.toString());
		if (!folder.exists()) {
			o.put("success", false);
			o.put("completionFB", folder + " should be a folder. It isn't.");
			return o;
		}
		String lastTmp = pieces[pieces.length - 1];
		File ft = new File(folder, lastTmp);
		if (ft.isDirectory()) {
			folder = ft;
			lastTmp = "";
		}
		final String last = lastTmp;
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (new File(dir, name).isHidden()) {
					return false;
				}
				return name.startsWith(last);
			}
		};
		String[] children = folder.list(filter);

		if (children.length == 0) {
			o.put("success", false);
			o.put("completionFB", "nothing in " + folder + " starting with " + last);
			return o;
		}

		StringBuilder builder = new StringBuilder();
		if (children.length == 1) {
			File f = new File(folder, children[0]);
			builder.append(f.getAbsolutePath());
			if (f.isDirectory()) {
				builder.append(sep);
				o.put("isDirectory", true);
			}
		} else {
			List<String> names = Arrays.asList(children);
			String common = findCommonStart(names, last);
			File f = new File(folder, common);

			builder.append(f.getAbsolutePath());

			if (f.isDirectory()) {
				builder.append(sep);
			}

			StringBuilder t = new StringBuilder();
			t.append("<ul>");
			for (String child : children) {
				t.append("<li>" + new File(child).getName() + "</li>");
			}
			t.append("</ul>");
			o.put("success", false);
			o.put("completionFB", t.toString());
		}
		o.put("browserLocation", builder.toString());
		return o;
	}

	private String findCommonStart(List<String> names, String last) {
		int i = last.length();
		String lastok = last;

		String first = names.get(0);

		while (i < first.length()) {
			String commonPrefix = first.substring(0, i);
			for (String s : names) {
				if (s.length() < i) {
					return lastok;
				}
				String prefix = s.substring(0, i);
				if (!commonPrefix.equals(prefix)) {
					return lastok;
				}
			}
			lastok = commonPrefix;
			i++;
		}
		return lastok;

	}

}
