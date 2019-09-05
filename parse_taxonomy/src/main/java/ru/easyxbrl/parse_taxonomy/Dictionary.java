package ru.easyxbrl.parse_taxonomy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.XbrlDefinition;

/**
 * Создаём словари для ядра таксономии
 * @author Андрей
 *
 */
public class Dictionary extends XsdSchema {

	/**
	 * Локальные словари: путь в архиве:словарь
	 */
	public static final Map<String, Dictionary> dicts =  new HashMap<>(); 
	
	/**
	 * 	Обходим LoadTaxonomyFromZip.nsOkudTable, формируем список словарей
	 *  далее создаём элементы словарей, подключаем к ним метки, создаём списки и т.п.
	 */
	public static void parse(Core core) {

		long startTime = System.currentTimeMillis();
		System.out.println("Обработка словарей");
		
		final Set<String> dictLocation = new HashSet<>(); // ссылки в архиве на словари
		dicts.clear();
		
		// заполняем уникальными словарями
		LoadTaxonomyFromZip.nsOkudTable.forEach( v -> {
			v.importElements.forEach( i -> { 
				if (!dictLocation.contains(i.schemaLocation)) {

					dictLocation.add(i.schemaLocation); 
					
					// Обрабатываем словарь
					try {
						
						
						final String path = (i.schemaLocation.startsWith("http:") 
								|| i.schemaLocation.startsWith("https:")) ? LoadTaxonomyFromZip.catalog.uriToPath(i.schemaLocation) : i.schemaLocation;
						
						final Dictionary dict = new Dictionary(core, path);
						dicts.put(dict.getLocation(), dict);

						// вносим в ядро список элементов, описания roleUri, адрес обработанного файла
						core.dicts.putDictionary(i.namespace, dict.elements);
						final String location = LoadTaxonomyFromZip.catalog.pathToUri(dict.getLocation());
						core.location.put(i.namespace, location);
						core.revLocation.put(location, i.namespace);
						
						dict.roleType.forEach( r -> core.dicts.putRoleUri(i.namespace, r.id, r.roleURI, r.definition));

					} catch (Exception e) {
						System.out.println(e.getStackTrace());
					}
				}
			});
		});

		// после обработки всех словарей, начинаем обработку 
		// текстовых представлений (label) и списков (definition)
		final Queue<XbrlDefinition> defList = new ConcurrentLinkedQueue<>(); // список секций / списков
		
		// словарей мало, один поток
		dicts.forEach((k,dict) -> {
			
			// обычно один словарь, не параллелим
			dict.label.forEach( l-> {
				try {
					// создаём метки для элементов непосредственно в ядре
					new XsdSchemaLabel(core, l, dicts, null);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
			
			dict.definition.parallelStream().forEach( l-> {
				try {
					final XsdSchemaDefinition def = new XsdSchemaDefinition(core, l, dicts, "definitionLink", "definitionArc", core.dicts);
					def.defList.forEach( d -> defList.add(d));
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		});
		
		// помещаем в ядро в одном потоке
		defList.forEach( d -> core.dicts.definitions.put(d.role, d));
		
		System.out.println("    обработано: "+dictLocation.size()+" (" + (System.currentTimeMillis() - startTime) + " ms).");
	}
	
	
	/**
	 * Обрабатываем словарь
	 * @param path
	 */
	Dictionary(Core core, String path) throws Exception {
		super(path);
	}
	
	
}
