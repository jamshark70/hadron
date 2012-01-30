HrKlangEditor : SCViewHolder {
	var <value, <>action, ampView, ampText, waveView, waveformButtons, numPartialSlider;
	var lastWaveDrawTime = 0, <>waveDrawInterval = 0.1;

	*new { |parent, bounds, numPartials = 10|
		^super.new.init(parent, bounds, numPartials)
	}

	init { |parent, bounds, numPartials|
		var width = bounds.width;

		this.view = CompositeView(parent, bounds);
		
		value = Array.fill(numPartials, 0).put(0, 1);

		waveView = MultiSliderView(parent, Rect(0, 0, width, bounds.height - 75))
		.value_(0.5 ! ((bounds.width / 4).nextPowerOfTwo).asInteger)
		.elasticMode_(1)
		.drawRects_(false)
		.drawLines_(true);

		ampView = MultiSliderView(parent, waveView.bounds)
		.background_(Color(1, 1, 1, 0.5))
		.value_(value.collect { |y| y * 0.5 + 0.5 })
		.elasticMode_(1)
		.indexThumbSize_(max(2.0, min(20, width / numPartials * 0.75)).roundUp)
		.valueThumbSize_(20)
		.action_({ |view|
			this.value = view.value * 2 - 1;
			this.doAction;
		});

		ampText = TextField(parent, Rect(0, bounds.height - 70, width, 20))
		.string_(this.string(value));

		numPartialSlider = EZSlider(parent, Rect(0, bounds.height - 45, width, 20), "num partials", [4, 45, \lin, 1, 10], { |view| this.numPartials = view.value }, numPartials);

		width = bounds.width - 30 / 3;
		waveformButtons = [
			[sawtooth: { |topPartial = 20| (1..topPartial).reciprocal }],
			[square: { |topPartial = 20| [(1, 3 .. topPartial).reciprocal, 0].lace(topPartial) }],
			[triangle: { |topPartial = 20| [(1, 3 .. topPartial).reciprocal.squared * #[1, -1], 0].lace(topPartial) }]
		].collect { |pair, i|
			Button(parent, Rect(5 + ((width+10) * i), bounds.height - 20, width, 20))
			.states_([[pair[0].asString]])
			.action_({
				this.value = pair[1].value(value.size);
			});
		};

		this.drawWave(value);
	}

	doAction { action.value(this) }

	value_ { |array|
		if(array.size != value.size) {
			defer {
				ampView.indexThumbSize = max(
					2.0,
					min(20, ampView.bounds.width / array.size * 0.75)
				).roundUp;
			};
		};
		value = array;
		this.drawWave(value);
		{
			ampView.value = value.collect { |y| y * 0.5 + 0.5 };
			ampText.string = this.string(value);
		}.defer(0.01);
	}

	numPartials_ { |num|
		num = num.asInteger;
		if(num != value.size) {
			this.value = value.copy.extend(num, 0);
		};
	}			

	string { |array|
		var temp;
		^array.collect { |item|
			temp = item.round(0.01).asString;
			if(temp.includes($.).not) { temp = temp ++ "." };
			temp ++ "0000".keep(max(0, 4 - temp.size))
		}
		.join(", ")
	}

	// this could be expensive - so, if two draw requests come in too close,
	// delay the second
	drawWave { |array|
		var drawFunc = {
			var signal = Signal.sineFill((view.bounds.width / 4).nextPowerOfTwo, array);
			waveView.value = signal.collectAs({ |y| y * 0.5 + 0.5 }, Array);
			lastWaveDrawTime = AppClock.beats;
		};
		if(AppClock.beats - lastWaveDrawTime < waveDrawInterval) {
			// if !=, then the value has changed since this request was scheduled
			// so there is no need to draw an outdated waveform
			AppClock.sched(lastWaveDrawTime + waveDrawInterval - AppClock.beats, {
				if(array == value, drawFunc);
			});
		} {
			// if we're here, then enough time passed: draw anyway
			drawFunc.defer;
		}
	}
}