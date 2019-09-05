package ru.easyxbrl.parse_taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class Catalog {
	private final Map<String, String> pathToUri = new TreeMap<>();
	
	/**
	 * Очищаем карту преобразований
	 */
	public void clear() {
		pathToUri.clear();
	}
	
	/**
	 * Получаем элементы преобразования путей
	 * @return
	 */
	public Map<String, String> getItems() {
		return pathToUri;
	}
	
	/**
	 * Добавляем пару: префикс URI - начало относительного пути
	 * @param uri
	 * @param path
	 */
	public void addUriAndPath(String uri, String path, String rootPrefix) {
		final String u = new String(uri);
		final String p = new String(changePath(rootPrefix,path)+"/");
		//System.out.println(u + " -> " + p);
		
		pathToUri.put(p, u);
	}
	
	/**
	 * Преобразуем путь в uri
	 * @param path
	 * @return
	 */
	public String pathToUri(String path) {
		String out = null;
		for (Entry<String,String> p:pathToUri.entrySet()) {
			if (path.startsWith(p.getKey())) {
				out = p.getValue()+path.substring(p.getKey().length());
				//System.out.println(" =-= " + out);
				break;
			}
		}
		return out;
	}

	/**
	 * Преобразуем из uri в path, если есть префиксы для преобразования
	 * @param uri
	 * @return null, если невозможно преобразование
	 */
	public String uriToPath(String uri) {
		String out = null;
		//if (uri.startsWith("http://") || uri.startsWith("https://")) 
		for (Entry<String,String> p:pathToUri.entrySet()) {
			if (uri.startsWith(p.getValue())) {
				out = p.getKey()+uri.substring(p.getValue().length());
				//System.out.println(" =+= " + uri + " -> " + out);
				break;
			}
		}
		return out;
	}
	
	/**
	 * Возвращает новый путь, на основе старого
	 * @param oldpath
	 * @param newpath
	 * @return
	 */
	static public String changePath(String oldpath, String newpath) {
		List<String> out = new ArrayList<String>();
		
		if (newpath.startsWith("http://") || newpath.startsWith("https://")) {
			out.add(newpath);
		} else {
			if (oldpath.length()>1 && newpath.length()>1) {
				
				String so = new String(oldpath.replace("\\", "/"));
				String sn = new String(newpath.replace("\\", "/"));
				
				if (so.substring(so.length()-1, so.length()).equals("/")) so = new String(so.concat(".")); 
				if (sn.substring(sn.length()-1, sn.length()).equals("/")) sn = new String(sn.concat("."));
				
				String[] ao = so.split(Pattern.quote("/"));
				String[] an = sn.split(Pattern.quote("/"));
		
				int lastIndexO = ao.length-1-1;
				int lastIndexN = an.length-1;
				
				while (lastIndexN>=0) {
					if (an[lastIndexN].equals(".")) {
					} else if (an[lastIndexN].equals("..")) {
						lastIndexO--;
					} else {
						out.add(an[lastIndexN]);
					}
					lastIndexN--;
				}
				while (lastIndexO>=0) {
					out.add(ao[lastIndexO]);
					lastIndexO--;
				}
				
				Collections.reverse(out);
			} 
		}
		
		return String.join("/",out);
	}
	

}
