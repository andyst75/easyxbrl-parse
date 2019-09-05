package ru.easyxbrl.parse_taxonomy;

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import ru.easyxbrl.core.Core;

public class SaveTaxonomyToZip {

	static public void save(Core core, OutputStream os) throws Exception {

		try(var zout = new ZipOutputStream( os , Charset.forName("UTF8")))
        {		
			System.out.println("Начало сохранения файла таксономии в поток.");
			final long start = System.currentTimeMillis();

			// запись версии ядра
			{
				final var entry=new ZipEntry(Core.fileCoreVersion);
				zout.putNextEntry(entry);
				zout.write(Core.version.getBytes());
				zout.closeEntry();
			}

			// запись версии таксономии
			{
				final var entry=new ZipEntry(Core.fileTaxonomyVersion);
				zout.putNextEntry(entry);
				zout.write(core.info.version.getBytes());
				zout.closeEntry();
			}
			
			// запись ядра
			{
				final var entry=new ZipEntry(Core.fileCore);
				zout.putNextEntry(entry);
				var outputStream = new java.io.ByteArrayOutputStream();
				try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
					oos.writeObject(core);
				} catch (Exception e) {
					e.printStackTrace();
				}
				zout.write(outputStream.toByteArray());
				zout.closeEntry();
			}
			
			System.out.println("Успешное сохранение файла за "+(System.currentTimeMillis()-start)+" мсек.");
        } catch (Exception e) {
        	System.out.println("Ошибка сохранения обработанного файла таксономии.");
        	e.printStackTrace();
        }

	}
}
