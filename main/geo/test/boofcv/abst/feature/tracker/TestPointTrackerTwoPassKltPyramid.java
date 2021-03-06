/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.abst.feature.tracker;

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.factory.feature.tracker.FactoryPointTrackerTwoPass;
import boofcv.struct.image.ImageFloat32;

/**
 * @author Peter Abeles
 */
public class TestPointTrackerTwoPassKltPyramid extends StandardPointTrackerTwoPass<ImageFloat32> {

	PkltConfig<ImageFloat32,ImageFloat32> config;

	public TestPointTrackerTwoPassKltPyramid() {
		super(false, true);
	}

	@Override
	public PointTrackerTwoPass<ImageFloat32> createTracker() {
		config = PkltConfig.createDefault(ImageFloat32.class, ImageFloat32.class);
		return FactoryPointTrackerTwoPass.klt(config, new ConfigGeneralDetector(200, 3, 1000, 0, true));
	}
}
