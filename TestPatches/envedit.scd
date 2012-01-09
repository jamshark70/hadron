// todo: handle curves

GUI.qt;

SwingOSC.default.boot;
ShutDown.add { SwingOSC.default.quit };
GUI.swing;


e.postcs;

v.curves = [4, -4];
v.refresh;

(
// e = Env.linen(0.2, sustainTime: 0.6, releaseTime: 0.2, level: 0.8);
// e = Env.perc(0.1, 0.9);
e = Env.adsr;

w = Window(\envtest, Rect(800, 100, 400, 300));
v = EnvelopeView(w, w.view.bounds.insetBy(2, 2));
v.value = [(#[0] ++ e.times.normalizeSum).integrate, e.levels];
v.curves = e.curves;
v.thumbSize = 8;
v.mouseUpAction = { |view, x, y, modifiers|
	var realExtent = view.bounds.extent,
	extent = realExtent - 8,
	normalized = Point(x, realExtent.y - y),
	normalThumbSize = Point(4, 4),  // half, for aboutPoint
	thumbRect,
	index = block { |break|
		view.value.flop.do { |pair, i|
			thumbRect = Rect.aboutPoint(Point(*pair) * extent + normalThumbSize, normalThumbSize.x, normalThumbSize.y);
			if(thumbRect.containsPoint(normalized)) { break.(i) };
		};
		nil
	};
	if(modifiers == 262144) {
		if(index.notNil) {
			"deleting thumb number %\n".postf(index);
			view.value = view.value.collect { |row|
				row.removeAt(index); row
			};
		} {
			index = block { |break|
				(view.value[0] * extent.x + normalThumbSize.x).doAdjacentPairs { |a, b, i|
					if(x.inclusivelyBetween(a, b)) { break.(i) };
				};
				break.(view.value[0].size);
			};
			"inserting point at %\n".postf(index);
			view.value = [
				view.value[0].insert(index+1, (x - normalThumbSize.x) / extent.x),
				view.value[1].insert(index+1, (realExtent.y - y - normalThumbSize.y) / extent.y)
			];
		};
	} 
	/*	later: set up curve-edit zones
		{
		index = block { |break|
		
		};
		}
	*/;
};

// enables ctrl-modifier not to turn into a drag
v.beginDragAction = { nil };

// u = UserView(w, v.bounds).background_(Color.clear);

// u.drawFunc_({
// 	var thumbSize = 8;
// 	var halfThumbSize = thumbSize / 2;   // pedantic...
// 	var extent = (v.bounds.extent.debug("bounds extent") - thumbSize).debug("my extent");
// 	var thumbBound;
// 	Pen.color = Color.blue(alpha: 0.6);
// 	v.value.flop.do { |pair|
// 		thumbBound = Rect.aboutPoint(Point(pair[0], 1.0 - pair[1]) * extent + Point(halfThumbSize, halfThumbSize), halfThumbSize, halfThumbSize);
// 		Pen.fillRect(thumbBound);
// 	};
// });

w.front;
)

w.refresh;

v.action = { "action".scramble.postln };
v.action = nil;

u.remove;

v.strings = "".dup(e.levels.size);

// problem: swing changes to main stroke/fill colors when dragging a point

{
	var string = "release",
	bounds = string.bounds.postln;
	v.setString(e.releaseNode, string)
	.setFillColor(e.releaseNode, Color(1.0, 0.8, 0.8))
	.setThumbWidth(e.releaseNode, bounds.width + 4)
	.setThumbHeight(e.releaseNode, bounds.height + 4);
	// v.strings = Array.fill(e.levels.size).put(e.releaseNode, string);
	// v.strokeColor = Color.black;
	// v.fillColor = Color.black;
}.fork(AppClock);


Rect(0.23456482076077, 0.86495875506788, 0.02020202020202, 0.027027027027027).rightBottom
Point( 0.24747474747475, 0.13513513513514 )

x = v.value;

u = UserView(w, v.bounds).background_(Color.clear);
u.drawFunc_({
	var halfThumbSize = 8 / 2;   // pedantic...
	var thumbBound;
	Pen.color = Color.blue(alpha: 0.6);
	/*v.value*/ x.flop.do { |pair|
		thumbBound = Rect.aboutPoint(Point(pair[0], 1.0 - pair[1]) * v.bounds.extent, halfThumbSize, halfThumbSize).debug;
		Pen.fillRect(thumbBound);
	};
});

u.refresh;
w.refresh;

w.children

u.remove;

v.remove



(
~envThing = Proto {
	~loopNodeColor = Color.green;
	~releaseNodeColor = Color.red(0.5);
	~fillColor = Color.black;
	~background = Color.white;
	~background_ = { |color|
		~background = color;
		~userView.background = color;
		currentEnvironment
	};
	~curveZoneColor = Color(0.8, 0.8, 1.0);
	~curveZoneWidth = 14;
	~trackingCurveIndex = nil;
	~thumbSize = 8;
	~envel = Env.linen;
	// ~lastDrawnEnv = nil;
	~envel_ = { |env|
		currentEnvironment.put(\envel, env);
		~releaseNode = env.releaseNode;
		~loopNode = env.loopNode;
		~viewCoords = [(#[0] ++ ~envel.times.normalizeSum).integrate, ~envel.levels];
		~curves = env.curves;
		// really this should be 'times.size' but there's a bug in qt
		if(~curves.isNumber) { ~curves = ~curves.dup(env.levels.size) };
		~refresh.();
		currentEnvironment
	};
	// aka 'value'
	// reassign to ~envel
	~next = {
		Env(~viewCoords[1],
			~viewCoords[0].differentiate.drop(1),
			~curves, ~releaseNode, ~loopNode
		)
	};

	~prep = { |parent, bounds|
		var zeroBounds = bounds.moveTo(0, 0);
		~view = CompositeView(parent, bounds);
		~userView = UserView(~view, zeroBounds)
		.background_(~background)
		.drawFunc_(e { |view|
			var point;
			// any curve that is a number gets a draggable zone
			(0 .. ~curves.size - 2).do { |i|
				if(~curves[i].isNumber) {
// ~viewCoords.flop[i .. i + 1].debug("normalized coords");
					point = ~scaleNormalPointToView.(Point(
						0.5 * (~viewCoords[0][i] + ~viewCoords[0][i+1]),
						~midpointYForCurve.(i)
					));
					Pen.color_(~curveZoneColor)
					.fillRect(Rect(
						point.x - (~curveZoneWidth * 0.5), 0,
						~curveZoneWidth, ~view.bounds.height
					))
					.color_(~fillColor)
					.fillOval(Rect.aboutPoint(point, ~thumbSize * 0.3, ~thumbSize * 0.3));
				};
			};
		});
		~envView = EnvelopeView(~view, zeroBounds)
			.background_(Color.clear);
		~envView.thumbSize = ~thumbSize;
		~envView.action = e { |view|
			~viewCoords = view.value;
			~userView.refresh;
			// ~curves = view.curves;   // nonimpl F-U-U-U-U-U-U-
		};

		~envView.mouseDownAction = e { |view, x, y, modifiers|
			var mouse;
			mouse = Point(x, y);
			if(modifiers == 0) {
				~trackingCurveIndex = block { |break|
					(0 .. ~curves.size-2).do { |i|
						if(~curves[i].isNumber and: {
							Rect.aboutPoint(
								~scaleNormalPointToView.(Point(
									0.5 * (~viewCoords[0][i] + ~viewCoords[0][i+1]),
									~midpointYForCurve.(i)
								)),
								~curveZoneWidth * 0.5, ~curveZoneWidth * 0.5
							).containsPoint(mouse)
						}) {
							break.(i)
						};
					};
					nil
				};
			};
		};
		~envView.mouseMoveAction = e { |view, x, y, modifiers|
			var mouse, lefty, righty;
			if(modifiers == 0 and: { ~trackingCurveIndex.notNil }) {
				mouse = ~scaleViewPointToNormal.(Point(x, y));
				lefty = ~viewCoords[1][~trackingCurveIndex];
				righty = ~viewCoords[1][~trackingCurveIndex + 1];
				if(mouse.y.exclusivelyBetween(min(lefty, righty), max(lefty, righty))) {
					~curves[~trackingCurveIndex] = ~curveForMidpointY.(
						~trackingCurveIndex, mouse.y
					);
					~refresh.();
				};
			};
		};
		// handles ctrl-click 
		~envView.mouseUpAction = e { |view, x, y, modifiers|
			var viewValue = view.value,
			realExtent = view.bounds.extent,
			extent = realExtent - ~thumbSize,
			mouse = Point(x, y),
			halfThumbSize = Point(4, 4),  // half, for aboutPoint
			thumbRect, index;
			case
			{ modifiers == 0 and: { ~trackingCurveIndex.notNil } } {
				~trackingCurveIndex = nil
			}
			{ modifiers == 262144 } {
				index = block { |break|
					viewValue.flop.do { |pair, i|
						thumbRect = Rect.aboutPoint(~scaleNormalPointToView.(Point(*pair)),
							halfThumbSize.x, halfThumbSize.y);
						if(thumbRect.containsPoint(mouse)) { break.(i) };
					};
					nil
				};
				if(index.notNil) {
					"deleting thumb number %\n".postf(index);
					~deletePoint.(index);
				} {
					index = block { |break|
						(viewValue[0] * extent.x + halfThumbSize.x).doAdjacentPairs { |a, b, i|
							if(x.inclusivelyBetween(a, b)) { break.(i) };
						};
						break.(viewValue[0].size);
					};
					"inserting point at %\n".postf(index);
					~insertPoint.(index+1,
						(x - halfThumbSize.x) / extent.x,
						(realExtent.y - y - halfThumbSize.y) / extent.y,
						~curves[index]
					);
				};
			};
			~refresh.();
			if((~envView.index ? -1) > 0) {
				~envView.deselectIndex(~envView.index);
			};
		};

		// enables ctrl-modifier not to turn into a drag
		~envView.beginDragAction = { nil };
		currentEnvironment
	};
	~insertPoint = { |index, time, level, curve|
		~fixSpecialNodes.(1, index);
		// Swing doesn't support valueAction_ -- F-U-U-U-U-U-U-
		~envView.value_([
			~viewCoords[0].insert(index, time),
			~viewCoords[1].insert(index, level)
		]).doAction;
		~curves = ~curves.insert(index, curve ?? { ~curves[index] });
		~envView.curves = ~curves;
		currentEnvironment
	};
	~deletePoint = { |index|
		~fixSpecialNodes.(-1, index);
		~envView.value_(~viewCoords.collect { |row|
			row.removeAt(index); row
		}).doAction;
		~curves.removeAt(index);
		~envView.curves = ~curves;
		currentEnvironment
	};
	~refresh = {
		~updateEnvView.();
		// for swing:
		// in qt, refreshing a composite view redraws all its children
		// in swing, a userview inside a compositeview doesn't get redrawn...
		~view.children.do(_.refresh);
		currentEnvironment
	};
	~updateEnvView = {
		~envView.value = ~viewCoords;
		~envView.curves = ~curves;
		~envView.thumbSize = ~thumbSize;
		~viewCoords[0].size.do { |i|
			if(i >= (~releaseNode ? 1e10)) {
				~envView.setFillColor(i, ~releaseNodeColor)
			} {
				if(i >= (~loopNode ? 1e10)) {
					~envView.setFillColor(i, ~loopNodeColor)
				} {
					~envView.setFillColor(i, ~fillColor);
				}
			};
		};
		currentEnvironment
	};
	~fixSpecialNodes = { |incr, index|
		if(~releaseNode.notNil and: { ~releaseNode >= index}) {
			~releaseNode = ~releaseNode + incr
		};
		if(~loopNode.notNil and: { ~loopNode >= index }) {
			~loopNode = ~loopNode + incr
		};
		currentEnvironment
	};
	~remove = {
		~view.remove;
		currentEnvironment
	};
	~scaleNormalPointToView = { |point|
		Point(point.x, 1.0 - point.y) * (~view.bounds.extent - ~thumbSize) + (~thumbSize * 0.5)
	};
	~scaleViewPointToNormal = { |point|
		(Point(point.x, ~view.bounds.height - point.y) - (~thumbSize * 0.5))
		/ (~view.bounds.extent - ~thumbSize)
	};
	~midpointYForCurve = { |index|
		if(~curves[index].isNumber) {
			ControlSpec(~viewCoords[1][index], ~viewCoords[1][index+1], ~curves[index])
			.map(0.5)
		} {
			(~viewCoords[1][index] + ~viewCoords[1][index+1]) * 0.5
		}
	};
	~curveForMidpointY = { |index, midval|
		var minval = ~viewCoords[1][index],
		maxval = ~viewCoords[1][index + 1],
		a, b, c, sqrterm, qresult, sgn = sign(maxval - minval);
		// the formula is unstable just above the average of minval and maxval
		// so mirror the midval around the average
		(midval > ((maxval + minval) * 0.5)).if({
			midval = minval + maxval - midval;
			sgn = sgn.neg;
		});
		a = midval - minval;
		b = minval - maxval;
		c = maxval - midval;
		sqrterm = sqrt(b.squared - (4 * a * c));
		(((qresult = (sqrterm - b) / (2 * a))).abs != 1).if({
			log(qresult.squared).abs * sgn
		}, {
			log(((b.neg - sqrterm) / (2 * a)).squared).abs * sgn
		});
	};
};
)

v.remove;

w = Window(\envtest, Rect(800, 100, 400, 300));
v = ~envThing.copy.prep(w, w.view.bounds.insetBy(2, 2)).envel_(Env.adsr);
w.front;

v.view

v.listVars

v.value.postcs
v.curves.put(1, 2); v.refresh;

v.curveZoneColor = Color(0.9, 0.9, 1.0);
v.refresh;

v.setCurve = { |index, curve|
	~curves[index] = curve;
	~refresh.();
};

v.setCurve(2, 2);
v.setCurve(1, \sin);

v.releaseNode_(2).refresh;


// scale funcs not working
v.viewCoords.flop.do { |pair|
	var x, y;
	#x, y = pair;
	[x, y, Rect.aboutPoint(v.scaleNormalPointToView(Point(*pair)), 4, 4)].postln;
}; ""

f = { |... args| args.debug("mouseup") };

v.envView.mouseUpAction = v.envView.mouseUpAction.addFunc(f);


// test class

w = Window(\envtest, Rect(800, 100, 400, 300));
v = HrEnvelopeView(w, w.view.bounds.insetBy(2, 2)).env_(Env.adsr);
w.front;

v.refresh;

v.curveZoneWidth = 10;
v.fillColor = Color.blue;
