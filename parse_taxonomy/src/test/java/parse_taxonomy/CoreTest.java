/**
 * 
 */
package parse_taxonomy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.XbrlDefinition;
import ru.easyxbrl.core.XbrlElement;
import ru.easyxbrl.core.XbrlTable;
import ru.easyxbrl.define.XbrlPeriodType;
import ru.easyxbrl.define.XbrlSubstitutionGroup;
import ru.easyxbrl.define.XbrlType;
import ru.easyxbrl.parse_taxonomy.LoadTaxonomyFromZip;
import ru.easyxbrl.parse_taxonomy.SaveTaxonomyToZip;

/**
 * @author Андрей
 *
 */
@SuppressWarnings("unused")
class CoreTest {

	static boolean make = true; // создавать ядро
	
	static Core core = null;
	
	/**
	 * Вызывается перед началом тестирования.
	 * Генерируем ядро таксономии, сохраняем его и потом загружаем заново для тестирования.
	 * Путь к тестовой таксономии - C:\XBRL\final_3_1.zip
	 * Путь к тестовому ядру таксономии - C:\XBRL\easyxbrl2_3_1.zip
	 * @throws Exception 
	 * @throws FileNotFoundException 
	 */
	@BeforeAll
	public static void init() throws FileNotFoundException, Exception{
	    System.out.println("Before All init() method called");
		long startTime = System.currentTimeMillis();

		if (make) {
		    System.out.println("\nСоздание подготовленного ядра таксономии");
			final Core core = new Core();
			LoadTaxonomyFromZip.parse(core, new FileInputStream("C:\\XBRL\\final_3_1.zip"));
			System.out.println("=== end create " + (System.currentTimeMillis() - startTime) + " ms.");
			SaveTaxonomyToZip.save(core, new FileOutputStream("C:\\XBRL\\easyxbrl2_3_1.zip"));
			System.out.println("=== " + (System.currentTimeMillis() - startTime) + " ms.");
		}

		System.out.println("\n\nЗагрузка ядра");
		core = Core.load(new FileInputStream("C:\\XBRL\\easyxbrl2_3_1.zip"));

	    System.out.println("\nНачало тестирования");
		
	    if (null==core) {
	    	 System.out.println("\nОшибочная версия ядра. Необходимо пересобрать отладочную версию таксономии.");
	    }
		Assertions.assertNotEquals(null, core);
	}
	
	@AfterAll
	static void endTest() {
	    System.out.println("\nТестирование закончено");
	}
	
	/**
	 * Вызывается перед каждым тестом
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	void setUp() throws Exception {
	}


	@Test
	void taxonomyInfo() {
		Assertions.assertEquals("20190501", core.info.version);
		Assertions.assertEquals("Таксономия XBRL Банка России версия 3.1", core.info.comments);
		Assertions.assertEquals("http://www.cbr.ru/xbrl/2019-05-01.zip", core.info.tpIdentifier);
	}

	
	@Test
	void dictionary() {
		Assertions.assertEquals("http://www.cbr.ru/taxonomy/bfo/dict/Purcb9_Label_1", core.dicts.getNamespaceByRoleId("http://www.cbr.ru/xbrl/udr/dom/mem-int", "Purcb9_Label_mem_1"));
		Assertions.assertEquals("http://www.cbr.ru/xbrl/bfo/dict#SoCIE_Exclusion2", core.dicts.getUriByNamespace("http://www.cbr.ru/xbrl/bfo/dict/SoCIE_Exclusion2"));
		Assertions.assertEquals("[Название Профессиональные участники рынка ценных бумаг МСФО 9 Member_1]", core.dicts.getDescription("http://www.cbr.ru/taxonomy/bfo/dict/Purcb9_Label_1"));
		Assertions.assertEquals("AccumulatedDepreciationAndAmortisationMember", core.dicts.getElement("http://xbrl.ifrs.org/taxonomy/2015-03-11/ifrs-full#ifrs-full_AccumulatedDepreciationAndAmortisationMember").name);
	}
	
	/**
	 * Проверка элемента справочника mem-int
	 * на конкретные значения, на текстовые метки
	 */
	@Test
	void memInt() {
		final XbrlElement el = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_ItogoNPMember");
		Assertions.assertEquals(el, core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int", "mem-int_ItogoNPMember"));
		Assertions.assertEquals(XbrlPeriodType.DURATION, el.periodType);
		Assertions.assertEquals(true, el.abstractElement);
		Assertions.assertEquals(true, el.nillable);
		Assertions.assertEquals(null, el.balance);
		Assertions.assertEquals(null, el.domain);
		Assertions.assertEquals("http://www.cbr.ru/xbrl/udr/dom/mem-int", el.parent);
		Assertions.assertEquals(XbrlType.DOMAIN_ITEM, el.type);
		Assertions.assertEquals("ItogoNPMember", el.name);
		Assertions.assertEquals("Итого", el.getLabelValue());
		Assertions.assertEquals("Итого [member]", el.getLabelValue("http://www.cbr.ru/2018/role/TechnicalLabel", "ru"));
		
		Assertions.assertEquals("http://www.cbr.ru/xbrl/udr/dom/mem-int.xsd", core.location.get("http://www.cbr.ru/xbrl/udr/dom/mem-int"));
		Assertions.assertEquals("http://www.cbr.ru/xbrl/udr/dom/mem-int", core.revLocation.get("http://www.cbr.ru/xbrl/udr/dom/mem-int.xsd"));
	}

	/**
	 * Проверка элемента справочника bfo-dict
	 * на конкретные значения, на текстовые метки
	 */
	@Test
	void bfoDict() {
		final XbrlElement el = core.dicts.getElement("http://www.cbr.ru/xbrl/bfo/dict#ifrs-ru_SrokDejstviyaLiczenzii");
		Assertions.assertEquals(el, core.dicts.getElement("http://www.cbr.ru/xbrl/bfo/dict", "ifrs-ru_SrokDejstviyaLiczenzii"));
		Assertions.assertEquals("http://www.cbr.ru/xbrl/bfo/dict/dictionary.xsd", core.location.get("http://www.cbr.ru/xbrl/bfo/dict"));
		Assertions.assertEquals(XbrlPeriodType.INSTANT, el.periodType);
		Assertions.assertEquals(false, el.abstractElement);
		Assertions.assertEquals(true, el.nillable);
		Assertions.assertEquals(null, el.domain);
		Assertions.assertEquals("http://www.cbr.ru/xbrl/bfo/dict", el.parent);
		Assertions.assertEquals(XbrlType.STRING, el.type);
		Assertions.assertEquals(XbrlSubstitutionGroup.ITEM, el.substitutionGroup);
		Assertions.assertEquals("SrokDejstviyaLiczenzii", el.name);
		Assertions.assertEquals("Срок действия лицензии", el.getLabelValue());
		Assertions.assertEquals("Срок действия лицензии", el.getLabelValue("http://www.cbr.ru/2018/role/TechnicalLabel", "ru"));
	}
	
	/**
	 * Проверка осей
	 */
	@Test
	void dimInt() {
		{
			final String dimint = "http://www.cbr.ru/xbrl/udr/dim/dim-int";
					
			final XbrlElement el = core.dicts.getElement(dimint + "#dim-int_ID_strokiTypedname");
			Assertions.assertEquals(el, core.dicts.getElement(dimint, "dim-int_ID_strokiTypedname"));
			Assertions.assertEquals(null, el.periodType);
			Assertions.assertEquals(false, el.abstractElement);
			Assertions.assertEquals(false, el.nillable);
			Assertions.assertEquals(null, el.domain);
			Assertions.assertEquals("http://www.cbr.ru/xbrl/udr/dim/dim-int", el.parent);
			Assertions.assertEquals("ID_strokiTypedname", el.name);

			Assertions.assertEquals("ID_DepozitScheta_TypedName",       core.dicts.getElement(dimint, "dim-int_ID_DepozitScheta_TypedName").name);
			Assertions.assertEquals("IDUpravlyayushhayaKompaniyaTaxis", core.dicts.getElement(dimint, "dim-int_IDUpravlyayushhayaKompaniyaTaxis").name);
			Assertions.assertEquals("TipyRasxUKNaInvestNakoplDlyaZHOVAxis", core.dicts.getElement(dimint, "dim-int_TipyRasxUKNaInvestNakoplDlyaZHOVAxis").name);
		}
		{
			final XbrlElement el = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_ID_strokiTaxis");
			Assertions.assertEquals(el, core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int", "dim-int_ID_strokiTaxis"));
			Assertions.assertEquals(XbrlPeriodType.DURATION, el.periodType);
			Assertions.assertEquals(XbrlType.STRING, el.type);
			Assertions.assertEquals(true, el.abstractElement);
			Assertions.assertEquals(true, el.nillable);
			Assertions.assertEquals("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_ID_strokiTypedname", el.typedDomain);
			Assertions.assertEquals(XbrlSubstitutionGroup.XBRLDT_DIMENSION_ITEM, el.substitutionGroup);
			Assertions.assertEquals("http://www.cbr.ru/xbrl/udr/dim/dim-int", el.parent);
			Assertions.assertEquals("ID_strokiTaxis", el.name);
			Assertions.assertEquals("Идентификатор строки", el.getLabelValue());
			Assertions.assertEquals("Идентификатор строки [Taxis]", el.getLabelValue("http://www.cbr.ru/2018/role/TechnicalLabel", "ru"));
		}
	}


	/**
	 * Проверка таблиц
	 */
	@Test
	void tables() {
		
		Assertions.assertEquals("http://www.cbr.ru/xbrl/nso/purcb/rep/2019-05-01/tab/SR_0420409",core.tables.get("http://www.cbr.ru/xbrl/nso/purcb/rep/2019-05-01/tab/SR_0420409").role);
		{
			XbrlTable tbl = core.tables.get("http://www.cbr.ru/xbrl/bfo/rep/2019-05-01/tab/FR_2_027_05c_01");

			Assertions.assertEquals(null, 
					tbl.getDescription("http://www.cbr.ru/xbrl/bfo/dict/Exclusion_062_technical"));
			Assertions.assertEquals("Распределение активов пенсионного плана"  
					, tbl.getDescription("http://www.cbr.ru/xbrl/bfo/rep/2019-05-01/tab/FR_2_027_05c_01"));
		}

		Assertions.assertEquals("SR_0420415_R2_PR2_3_1", 
				core.tables.get("http://www.cbr.ru/xbrl/nso/purcb/rep/2019-05-01/tab/SR_0420415").getUriByNamespace("http://www.cbr.ru/xbrl/nso/purcb/rep/2019-05-01/tab/SR_0420415/SR_04020415_R2_PR2_3/1").split("#")[1]);

		{
			XbrlTable tbl = core.tables.get("http://www.cbr.ru/xbrl/bfo/rep/2019-05-01/tab/FR_4_004_19c_01");
			
			Assertions.assertFalse(tbl.definitions.isEmpty());
			Assertions.assertFalse(tbl.presentation.isEmpty());
			
		}
		
	}


	/**
	 * Проверка списков
	 */
	@Test
	void definitionList() {
		
		{
			XbrlDefinition x = core.dicts.definitions.get("http://www.cbr.ru/xbrl/nso/dic/purcb-dic/OKATOList");
			Assertions.assertEquals("http://www.cbr.ru/xbrl/nso/dic/purcb-dic/OKATOList", x.role);
			Assertions.assertEquals(1 , x.child.size());
			Assertions.assertEquals(87, x.child.get(0).child.size());
			Assertions.assertEquals("OKATO71000TyumenskayaOblastMember", x.child.get(0).child.get(56).label);
			Assertions.assertEquals(core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_OKATO71000TyumenskayaOblastMember"),
					x.child.get(0).child.get(56).element);
		}
		
		{
			XbrlDefinition x = core.dicts.definitions.get("http://www.cbr.ru/xbrl/nso/dic/purcb-dic/Priznak_Nulevogo_OtchetaList");
			Set<XbrlElement> set = new HashSet<>();
			x.child.get(0).child.forEach( c -> set.add(c.element));
			Assertions.assertEquals(true, set.contains(core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_DaMember")));
			Assertions.assertEquals(true, set.contains(core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_NetMember")));
			Assertions.assertEquals("Да", core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_DaMember").getLabelValue());
			Assertions.assertEquals("Нет", core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_NetMember").getLabelValue());
		}
		
	}


	
	/**
	 * Проверка правил элементов таксономии (xsd restriction)
	 */
	@Test
	void restrictions() {
		
		XbrlElement x = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_BrokerskijSchetTypedName");
		Assertions.assertEquals(false, x.nillable);
		Assertions.assertEquals(false, x.restrictions.isEmpty());
		Assertions.assertEquals(XbrlType.W3_STRING, x.restrictions.get(0).base);
		Assertions.assertEquals(1, x.restrictions.get(0).minLength);
		Assertions.assertEquals(-1, x.restrictions.get(0).maxLength);
		Assertions.assertEquals(true, x.restrictions.get(0).test(null));
		Assertions.assertEquals(true, x.restrictions.get(0).test("123"));
		Assertions.assertEquals(true, x.restrictions.get(0).test(""));

		x = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_KodDsTypedname");
		Assertions.assertEquals(true, x.restrictions.get(0).test("101100"));
		Assertions.assertEquals(false, x.restrictions.get(0).test("101103"));

		x = core.dicts.getElement("http://www.cbr.ru/xbrl/nso/ins/dic/ins-dic#ins-dic_Data_Chlenstvo_Sro_Obed_Ssd");
		Assertions.assertEquals(XbrlType.DATE, x.restrictions.get(0).base);
		Assertions.assertEquals(true, x.restrictions.get(0).test("2019-01-01"));
		Assertions.assertEquals(false, x.restrictions.get(0).test("20190101"));
		
	}

	
	/**
	 * Проверка элементов dimension-defaults
	 */
	@Test
	void dimensionDefaults() {
		
		{
			XbrlElement axis    = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_TechnicalAxis");
			XbrlElement element = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_ItogoNPMember");

			Assertions.assertNotNull(axis);
			Assertions.assertNotNull(element);
			
			Assertions.assertEquals(true, core.dicts.isDefaults(axis, element));
		}

		{
			XbrlElement axis = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_NaczVal_RubEkvAxis");
			XbrlElement element = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_NacValiRubEkvitogoMember");

			Assertions.assertNotNull(axis);
			Assertions.assertNotNull(element);
			
			Assertions.assertEquals(true, core.dicts.isDefaults(axis, element));
		}
		

		{
			XbrlElement axis = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dim/dim-int#dim-int_Kod_Vid_Operaczii_DenSredstvaAxis");
			XbrlElement element = core.dicts.getElement("http://www.cbr.ru/xbrl/udr/dom/mem-int#mem-int_VseOperacziiMember");

			Assertions.assertNotNull(axis);
			Assertions.assertNotNull(element);
			
			Assertions.assertEquals(true, core.tables.get("http://www.cbr.ru/xbrl/nso/brk/rep/2019-05-01/tab/sr_0420001_ODS").isDefaults(axis, element));
		}

	}
	
	
}
