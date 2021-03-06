package piprint;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialFactory;

public class ThermalPrinter2 {
	static final byte ESC = 27;
	static final byte _7 = 55;
	static final byte DC2 = 18;
	static final byte DIEZE = 35;
	private final Serial serial;
	private PrintImageThread pit;
	private PrinterConfig printerConfig;

	public ThermalPrinter2() {
		serial = SerialFactory.createInstance();
		serial.open(Serial.DEFAULT_COM_PORT, 115200);
	}

	public void configPrinterWithDefault() {
		printerConfig = new PrinterConfig();
		configPrinter(printerConfig);
	}

	public void configPrinter(PrinterConfig config) {
		printerConfig = config;
		PrinterConfigThread pct = new PrinterConfigThread(printerConfig);
		pct.start();
	}

	public void printImage(byte[] img, int width, int length) {
		if (isPrinting()) return;
		pit = new PrintImageThread(img, width, length);
		pit.start();
	}

	public void printImage(DitheredImage image) {
		if (isPrinting()) return;
		pit = new PrintImageThread(image);
		pit.start();
	}

	public void printImage(String file) {
		if (isPrinting()) return;
		DitheredImage imageToPrint = new DitheredImage(file, 128, 384);
		this.printImage(imageToPrint);;
	}

	public boolean isPrinting() {
		return pit != null && pit.isAlive();
	}

	public void motorStep() {
		if (isPrinting()) {
			pit.addOnePrintedLine();
		}
	}

	private class PrinterConfigThread extends Thread {
		private PrinterConfig config;
		private byte[] sequence;

		public PrinterConfigThread(PrinterConfig config) {
			this.config = config;

			sequence = new byte[] {
					ESC, _7, 
					config.heatingMaxDot, config.heatTime, config.heatInterval, 
					DC2, DIEZE,
					(byte) ((config.printBreakTime << 5) | config.printDensity),
					0x1D, 0x61, (byte) 0xFF // auto report ?
			};
		}

		@Override
		public void run () {

			System.out.println("Printer: start config");

			for (int i = 0; i < sequence.length; i++) {
				serial.write(sequence[i]);
			}

			System.out.println("Printer: end config");
		}
	}

	private class PrintImageThread extends Thread {
		private byte[] imageBytes;
		private int width, length;
		private AtomicBoolean hold = new AtomicBoolean();
		private AtomicInteger linePrinted = new AtomicInteger(0);

		public PrintImageThread(byte[] img, int width, int length) {
			this.imageBytes = img;
			this.length = length;
			this.width = width;
			hold.set(false);
		}

		public PrintImageThread(DitheredImage image) {
			this.imageBytes = image.getImageInBytesForPrint();
			this.width = image.getImageWidth();
			this.length = image.getImageLength();
			hold.set(false);
		}

		public void addOnePrintedLine() {
			linePrinted.incrementAndGet();
		}

		@Override
		public void run () {
			byte[] printLineCommand ;

			System.out.println("Printer: start print bitmap");

			try {
				sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			for (int lineSent = 0; lineSent < length; lineSent++) {

				if (lineSent%255 == 0) {
					int remainingLines = length-lineSent;
					if (remainingLines > 255) remainingLines = 255;
					printLineCommand = new byte[] {0x12, 0x2A, (byte) remainingLines, 48};

					for (int j = 0; j < printLineCommand.length; j++) {
						serial.write(printLineCommand[j]);
					}
				}

				for (int j = 0; j < 48; j++) {
					byte imageByte = imageBytes[(lineSent*48)+j];
					serial.write(imageByte);
				}

				System.out.println("Line sent: "+lineSent+", linePrinted: "+linePrinted.get());

				if (lineSent > 50) {
					
					int i=0;
					while ( (lineSent - linePrinted.get()) > 80 && i < 20) {
						try { 
							sleep(1);	
							i++;
							System.out.print(".");
						} catch (InterruptedException e) {	}
					}
					
				}

			}

			try {
				sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
			String date = sdf.format(new Date());

			serial.write(date+"\n");
			serial.write((char) 0x0A);
			serial.write((char) 0x0A);

			System.out.println("Printer: end bitmap");


		}


	}

}
