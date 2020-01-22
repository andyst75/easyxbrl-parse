package parse_taxonomy;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.XbrlDefElement;
import ru.easyxbrl.core.XbrlDefinition;
import ru.easyxbrl.core.XbrlTable;
import ru.easyxbrl.define.XbrlArcrole;

class TableTest {
	
	static Core core = null;
	
	@BeforeAll
	public static void init() throws FileNotFoundException, Exception{
	    System.out.println("\nНачало тестирования таблиц");
		if (CoreTest.core == null) CoreTest.init();
		core = CoreTest.core;
		Assertions.assertNotEquals(null, core);
		Assertions.assertEquals("20190501", core.info.version);
	}

	@AfterAll
	static void endTest() {
	    System.out.println("\nТестирование таблиц закончено");
	}
	

	@Test
	void FR_2_001_01c_01() {
		XbrlTable tbl = core.tables.get("http://www.cbr.ru/xbrl/bfo/rep/2019-05-01/tab/FR_2_001_01c_01");
		Assertions.assertNotEquals(null, tbl);
		
		XbrlDefinition def = tbl.definitions.get("http://www.cbr.ru/xbrl/bfo/rep/2019-05-01/tab/FR_2_001_01c_01");
		Assertions.assertNotEquals(null, def);
		
		Assertions.assertEquals("http://www.cbr.ru/xbrl/bfo/rep/2019-05-01/tab/FR_2_001_01c_01", def.role);
		Assertions.assertEquals(1, def.child.size());
		
		XbrlDefElement root = def.child.get(0);

		Assertions.assertEquals(null, root.parent);
		Assertions.assertEquals(7, root.child.size());
		
		
		Map<String, XbrlDefElement> rootMap = new TreeMap<>(); 
		root.child.forEach( c -> rootMap.put(c.label, c));
		
		//root.child.forEach( c -> System.out.println(c.label + " " + c.child.size() + " " + c.notAll.size()));
		
		Assertions.assertEquals(0, rootMap.get("ДенежныеСредства").child.size());
		Assertions.assertEquals(1, rootMap.get("ДенежныеСредстваTable").child.size());
		Assertions.assertEquals(0, rootMap.get("ДенежныеСредстваВКассе").child.size());
		Assertions.assertEquals(0, rootMap.get("ДенежныеСредстваВПути").child.size());
		Assertions.assertEquals(0, rootMap.get("ДенежныеСредстваНаРасчетныхСчетах").child.size());
		Assertions.assertEquals(0, rootMap.get("ДенежныеСредстваПереданныеВДоверительноеУправление").child.size());
		Assertions.assertEquals(0, rootMap.get("ДенежныеСредстваПрочее").child.size());

		Assertions.assertEquals("ПоТипамСтоимостиAxis", rootMap.get("ДенежныеСредстваTable").child.get(0).label);
		Assertions.assertEquals(1, rootMap.get("ДенежныеСредстваTable").child.get(0).child.size());
		
		
		//System.out.println("["+rootMap.get("ДенежныеСредстваВКассе").notAll.get(0).label + "]");
		Assertions.assertEquals(1, rootMap.get("ДенежныеСредстваВКассе").notAll.size());
		Assertions.assertEquals("IsklyuchenieCash", rootMap.get("ДенежныеСредстваВКассе").notAll.get(0).label);
		Assertions.assertEquals(XbrlArcrole.NOT_ALL, rootMap.get("ДенежныеСредстваВКассе").notAll.get(0).arcrole);
		Assertions.assertEquals("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_093_technical", rootMap.get("ДенежныеСредстваВКассе").notAll.get(0).targetRole);
		
		XbrlDefinition excl093 = core.dicts.definitions.get("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_093_technical");
		
		Assertions.assertEquals("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_093_technical", excl093.role);
		Assertions.assertEquals(1, excl093.child.size());
		Assertions.assertEquals("IsklyuchenieCash", excl093.child.get(0).element.name);

		Assertions.assertEquals("PoTipamStoimostiAxis", excl093.child.get(0).child.get(0).element.name);
		Assertions.assertEquals(1, excl093.child.get(0).child.get(0).child.size());

		Assertions.assertEquals("RezervPodObesczenenieMember", excl093.child.get(0).child.get(0).child.get(0).element.name);
		Assertions.assertEquals(0, excl093.child.get(0).child.get(0).child.get(0).child.size());
	
		//rootMap.get("ДенежныеСредстваВКассе").notAll.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()+ " " + c.targetRole));
		
		/*

				core.dicts.definitions.get("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_093_technical").child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()+ " " + c.targetRole));
		
		core.dicts.definitions.get("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_093_technical").child.get(0).child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()+ " " + c.targetRole));
		core.dicts.definitions.get("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_093_technical").child.get(0).child.get(0).child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()+ " " + c.targetRole));

		rootMap.get("ДенежныеСредстваTable").child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()));		
		rootMap.get("ДенежныеСредстваTable").child.get(0).child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()));
		

		rootMap.get("ДенежныеСредстваВКассе").child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()+ " " + c.targetRole));		
		

		rootMap.get("ДенежныеСредстваВПути").child.forEach( c -> System.out.println(c.label + " " + c.arcrole + " " + c.element.name + " " + c.child.size()+ " " + c.targetRole));		
		*/
		
	}

	@Test
	void sr_819_ODS_5_Dvizhen_sredst() {
		XbrlTable tbl = core.tables.get("http://www.cbr.ru/xbrl/nso/nfo/rep/2019-05-01/tab/sr_819_ODS");
		Assertions.assertNotEquals(null, tbl);
		
		XbrlDefinition def = tbl.definitions.get("http://www.cbr.ru/xbrl/nso/nfo/rep/2019-05-01/tab/sr_819_ODS_5_Dvizhen_sredst");
		Assertions.assertNotEquals(null, def);
		
		Assertions.assertEquals("http://www.cbr.ru/xbrl/nso/nfo/rep/2019-05-01/tab/sr_819_ODS_5_Dvizhen_sredst", def.role);
		Assertions.assertEquals(1, def.child.size());
		
		XbrlDefElement root = def.child.get(0);

		
		Assertions.assertEquals(null, root.parent);
		Assertions.assertEquals(3, root.child.size());
		
		
		Map<String, XbrlDefElement> rootMap = new TreeMap<>(); 
		root.child.forEach( c -> rootMap.put(c.label, c));
		
		
		Assertions.assertEquals(0, rootMap.get("Spisan_sredst").child.size());
		Assertions.assertEquals(0, rootMap.get("Zachisl_sredst").child.size());
		Assertions.assertEquals(3, rootMap.get("hyp").child.size());
		
		XbrlDefElement hyp = rootMap.get("hyp");
		
		Assertions.assertEquals("Rek_kred_org_i_schetaTaxis",hyp.child.get(0).label);
		Assertions.assertEquals("Kod_ValyutyAxis",hyp.child.get(1).label);
		Assertions.assertEquals("Kod_Vid_OperacziiAxis",hyp.child.get(2).label);
		
		
		XbrlDefElement Kod_ValyutyAxis = hyp.child.get(1);
		Assertions.assertEquals("http://www.cbr.ru/xbrl/nso/nfo/dic/Kod_ValyutyAxis", Kod_ValyutyAxis.targetRole);

		//System.out.println(Kod_ValyutyAxis.label + " " + Kod_ValyutyAxis.targetRole);
		
		
		XbrlDefinition dic = core.dicts.definitions.get("http://www.cbr.ru/xbrl/nso/nfo/dic/Kod_ValyutyAxis");
		 
		Assertions.assertEquals(163,dic.child.get(0).child.get(0).child.size());
		//System.out.println(dic.child.get(0).child.get(0).child.size());
		
		//.child.forEach( c-> System.out.println(c.label));
		//System.out.println(root.child.size());
		
		
	}
	
}
