package com.example.picencrypt.utils;

import java.util.Arrays;

public abstract class BasePicEncryptScramble extends ImageScramble {
	protected double key;
	
	public BasePicEncryptScramble(Image image, double key) {
		this(image.getPixels(), image.getWidth(), image.getHeight(), key);
	}

	public BasePicEncryptScramble(int[] pixels, int width, int height, double key) {
		super(pixels, width, height);
		this.key = key;
	}
	
	protected double[][] generateLogistic(double x1, int n) {
		if (n <= 0) return new double[0][2];
		double[][] arr = new double[n][2];
		double x = x1;
		if (Double.isNaN(x) || Double.isInfinite(x)) {
			x = 0.5;
		} else {
			x = Math.abs(x);
			x = x - Math.floor(x);
			if (x <= 1e-7 || x >= (1.0 - 1e-7)) {
				x = 0.5;
			}
		}
		arr[0][0] = x;
		arr[0][1] = 0;
		for (int i = 1; i < n; ++i) {
			x = 3.9999999 * x * (1 - x);
			arr[i][0] = x;
			arr[i][1] = i;
		}
		return arr;
	}
	
	protected int[] getSortedPositions(double[][] logistacMap, int positionSize) {
		Arrays.sort(logistacMap, (a, b) -> Double.compare(a[0], b[0]));
		int[] positions = new int[positionSize];
		for (int i = 0; i < positionSize; ++i) {
			positions[i] = (int) logistacMap[i][1];
		}
		return positions;
	}
}
