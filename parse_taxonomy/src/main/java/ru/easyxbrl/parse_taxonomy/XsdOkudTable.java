package ru.easyxbrl.parse_taxonomy;

/**
 * Промежуточное преобразование таблицы отчётности, 
 * @author a.starikov
 *
 */
public class XsdOkudTable extends XsdSchema {
	
	
	/*
	 
	 в заголовке: targetNamespace="http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409"
	 в аннотации: link:roleType roleURI="http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409/SR_0420409" id="SR_0420409"
	 
	 т.е. http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409/SR_0420409 = http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409#SR_0420409
	 
	 1. обрабатываем заголовок файла ( SR_0420417.xsd )
	 2. обрабатываем xsd:annotation / xsd:appinfo : link:linkbaseRef, link:roleType
	 3. обрабатываем import
	 
	 */
	
	
	/**
	 * Основной конструктор
	 * @param location
	 * @throws Exception
	 */
	public XsdOkudTable(String location) throws Exception {
		super(location);
	}
	

	/**
	 * Обходим таблицу nsToLocation, формируем таблицу соответствий nsOkudTable
	 */
	static void parseFromNsToLocation() {
		long startTime = System.currentTimeMillis();
		System.out.println("Обработка служебных записей таблиц в точках входа");
		
		LoadTaxonomyFromZip.nsToLocation.forEach((k,v) -> {
			try {
				LoadTaxonomyFromZip.nsOkudTable.add(new XsdOkudTable(v));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		
		System.out.println("    обработано: "+LoadTaxonomyFromZip.nsToLocation.size()+" (" + (System.currentTimeMillis() - startTime) + " ms).");
	}
	
}
