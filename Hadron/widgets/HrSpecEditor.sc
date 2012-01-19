HrSpecEditor : SCViewHolder {
	var value, <>action;
	var specMin, specMax, specWarp, specStep;
	var badSpecRoutines, didSilentUpdate;

	*new { |parent, bounds, margin(Point(5, 5)), labelPrefix = "map", labelWidth = 65|
		^super.new.init(parent, bounds, margin, labelPrefix, labelWidth)
	}

	value {
		if(this.specIsValid) {
			^value
		} {
			Error("Spec has invalid parameters:" + didSilentUpdate.asString).throw;
		}
	}

	value_ { |spec|
		value = HrControlSpec.newFrom(spec.asSpec);
		defer {
			specMin.value = value.minval;
			specMax.value = value.maxval;
			specWarp.value = value.warp.asSpecifier;
			specStep.value = value.step;
		}
	}

	init { |parent, bounds, margin, labelPrefix, labelWidth|
		var half = bounds.extent * 0.5,
		height = (bounds.height - (3 * margin.y)) * 0.5,
		numberWidth = (bounds.width - (5 * margin.x) - (2 * labelWidth)) * 0.5,
		secondRowCol = half + (margin * 0.5);

		var expWarpIsBad = {
			specMin.value.sign != specMax.value.sign
			or: { specMin.value == 0 or: { specMax.value == 0 } }
		};
		badSpecRoutines = ();

		this.view = CompositeView(parent, bounds);

		StaticText(view, Rect(margin.x, margin.y, labelWidth, height))
		.string_(labelPrefix + "min");
		StaticText(view, Rect(secondRowCol.x, margin.y, labelWidth, height))
		.string_(labelPrefix + "max");
		StaticText(view, Rect(margin.x, secondRowCol.y, labelWidth, height))
		.string_(labelPrefix + "warp");
		StaticText(view, Rect(secondRowCol.x, secondRowCol.y, labelWidth, height))
		.string_(labelPrefix + "step");

		specMin = NumberBox(view, Rect((margin.x * 2) + labelWidth, margin.y, numberWidth, height))
		// .value_(value.minval)
		.maxDecimals_(5)
		.action_({ |view|
			if(value.warp.class == ExponentialWarp and: { expWarpIsBad.value }) {
				this.changed(\message, "Invalid warp: Exponential warp endpoints must be the same sign and nonzero", -1);
				didSilentUpdate = didSilentUpdate.add(\minval);
				value.minval = view.value;
				badSpecRoutines[\minval] ?? {
					badSpecRoutines[\minval] = Routine({
						var colors = Pseq([Color(1.0, 0.86, 0.86), Color.white], inf).asStream;
						loop {
							view.background = colors.next;
							0.75.wait;
						};
					}).play(AppClock);
				};
			} {
				value.minval = view.value;
				this.prStopRoutines;
				view.background = Color.white;
				action.value(this, \minval);
			};
		});

		specMax = NumberBox(view,
			Rect(secondRowCol.x + margin.x + labelWidth, margin.y, numberWidth, height)
		)
		// .value_(value.maxval)
		.maxDecimals_(5)
		.action_({ |view|
			if(value.warp.class == ExponentialWarp and: expWarpIsBad) {
				this.changed(\message, "Invalid warp: Exponential warp endpoints must be the same sign and nonzero", -1);
				didSilentUpdate = didSilentUpdate.add(\maxval);
				value.maxval = view.value;
				badSpecRoutines[\maxval] ?? {
					badSpecRoutines[\maxval] = Routine({
						var colors = Pseq([Color(1.0, 0.86, 0.86), Color.white], inf).asStream;
						loop {
							view.background = colors.next;
							0.75.wait;
						};
					}).play(AppClock);
				};
			} {
				value.maxval = view.value;
				this.prStopRoutines;
				view.background = Color.white;
				action.value(this, \maxval);
			};
		});
		specWarp = TextField(view, Rect((margin.x * 2) + labelWidth, secondRowCol.y, numberWidth, height))
		// .string_("lin")
		.action_({ |view|
			var warp, continue = true;
			try {
				warp = view.string;
				if(warp.every(_.isAlpha)) {
					warp = warp.asSymbol;
				} {
					warp = warp.asFloat;
					if(warp == 0 and: {
						view.string.any { |char|
							char.isDecDigit.not and: { char != $. }
						}
					}) {
						// this, of course, may throw a different error
						// which is why all of this is in a try{} block
						warp = view.string.interpret
					};
				};
				if(warp.respondsTo(\asWarp).not) {
					Error("Warp must be number, symbol or a valid SC expression").throw;
				};
				warp = warp.asWarp(value);  // throws error if a wrong symbol
				if(warp.class == ExponentialWarp and: expWarpIsBad) {
					Error("Exponential warp endpoints must be the same sign and nonzero").throw
				};
			} { |err|
				if(err.isKindOf(Exception)) {
					continue = false;
					err.errorString.postln;
					if(err.errorString.beginsWith("ERROR: Exponential warp endpoints")) {
						// we will update the spec but NOT call the GUI action now
						// until the wrong warp is fixed
						// (it might be fixed by changing another field)
						value.warp = warp;
						didSilentUpdate = didSilentUpdate.add(\warp);
					};
					this.changed(\message, "Invalid warp: " ++ err.errorString[7..], -1);
					badSpecRoutines[\warp] ?? {
						badSpecRoutines[\warp] = Routine({
							var colors = Pseq([Color(1.0, 0.86, 0.86), Color.white], inf).asStream;
							loop {
								view.background = colors.next;
								0.75.wait;
							};
						}).play(AppClock);
					};
				};
			};
			if(continue) {
				value.warp = warp;
				this.prStopRoutines;
				view.background = Color.white;
				action.value(this, \warp);
			};
		});
		specStep = NumberBox(view,
			Rect(secondRowCol.x + margin.x + labelWidth, secondRowCol.y, numberWidth, height)
		)
		.maxDecimals_(5)
		.action_({ |view|
			value.step = view.value;
			action.value(this, \step);
		});

		this.value = nil;  // will be .asSpec'ed
	}

	viewDidClose {
		didSilentUpdate = nil;
		this.prStopRoutines;
	}

	prStopRoutines {
		badSpecRoutines.do(_.stop);
		badSpecRoutines.clear;
		[specMin, specMax, specWarp].do { |view|
			if(view.notClosed) {
				view.background = Color.white;
			};
		};
		if(didSilentUpdate.size > 0) {
			didSilentUpdate.do { |param| action.value(this, param) };
			didSilentUpdate = nil;
		};
	}

	specIsValid { ^didSilentUpdate.isNil }
}
