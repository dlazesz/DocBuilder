/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hu.tempus.HtmlGui;

/**
 *
 * @author TeMPuS
 */
public class GlobalConfig {
	public static String UserAgent;
	public static boolean Debug;

	public static void load(Config config) {
		UserAgent = config.getValue("user-agent", "htmlgui/1.0");
		Debug  = !config.getValue("debug", "0").equals("0");
	}
}
