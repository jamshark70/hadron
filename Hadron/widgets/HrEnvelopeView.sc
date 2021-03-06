/*
HrEnvelopeView added by H. James Harkins
*/

HrEnvelopeView : SCViewHolder {
	var <env, <curves, <releaseNode, <loopNode,
	<>action, <>insertAction, <>deleteAction, <>curveAction,
	<loopNodeColor,
	<releaseNodeColor,
	<fillColor,
	<background,
	<curveZoneColor,
	<curveZoneWidth = 14,
	<thumbSize = 8,
	trackingCurveIndex, trackingPointIndex,  // internal
	envView, userView,
	viewCoords;

	*new { |parent, bounds|
		^super.new.init(parent, bounds)
	}

	init { |parent, bounds|
		var zeroBounds = bounds.moveTo(0, 0);

		// inits that can't be done in the instance var declarations
		loopNodeColor = Color.green;
		releaseNodeColor = Color.red(0.5);
		fillColor = Color.black;
		background = Color.white;
		curveZoneColor = Color(0, 0, 1.0, alpha: 0.12); // Color(0.9, 0.9, 1.0);

		this.view = CompositeView(parent, bounds);
		if(GUI.id == \qt) {
			envView = EnvelopeView(view, zeroBounds).background_(background);
			userView = UserView(envView, zeroBounds).background_(Color.clear);
			// pass mouse movements thru to the envView
			userView.mouseDownAction_(false)
			.mouseUpAction_(false)
			.mouseMoveAction_(false);
		} {
			userView = UserView(view, zeroBounds).background_(background);
			envView = EnvelopeView(view, zeroBounds).background_(Color.clear);
		};

		userView.drawFunc_({ |view|
			var point, yboundsPoint;
			// any curve that is a number gets a draggable zone
			(0 .. curves.size - 2).do { |i|
				if(curves[i].isNumber) {
					point = this.scaleNormalPointToView(Point(
						0.5 * (viewCoords[0][i] + viewCoords[0][i+1]),
						this.midpointYForCurve(i)
					));
					yboundsPoint = this.scaleNormalPointToView(Point(
						0, // x is irrelevant
						viewCoords[1][i..i+1]
					));
					// qt handles fillRect in any direction
					// swing doesn't
					if(yboundsPoint.y[1] < yboundsPoint.y[0]) {
						yboundsPoint.y = yboundsPoint.y.swap(0, 1);
					};
					Pen.color_(curveZoneColor)
					.fillRect(Rect(
						point.x - (curveZoneWidth * 0.5), yboundsPoint.y[0],
						curveZoneWidth, yboundsPoint.y[1] - yboundsPoint.y[0]
					))
					.color_(fillColor)
					.fillOval(Rect.aboutPoint(point, thumbSize * 0.3, thumbSize * 0.3));
				};
			};
		});

		envView.thumbSize = thumbSize;
		envView.action = { |view|
			var mouse, left, right;
			viewCoords = view.value;
			if(trackingPointIndex.notNil) {
				mouse = viewCoords[0][trackingPointIndex];
				left = viewCoords[0][trackingPointIndex - 1] ? 0;
				right = viewCoords[0][trackingPointIndex + 1] ? 1;
				if(mouse.inclusivelyBetween(left, right).not) {
					viewCoords[0][trackingPointIndex] = clip(mouse, left, right);
					this.refresh;
				};
			};
			userView.refresh;
			this.doAction;
		};

		envView.mouseDownAction = { |view, x, y, modifiers|
			var mouse;
			mouse = Point(x, y);
			// ignore bits except the main keyboard mods
			// Cocoa seems to set some bits that are not relevant :-|
			modifiers = modifiers & 0x001E0000;
			if(modifiers == 0) {
				trackingCurveIndex = block { |break|
					(0 .. curves.size-2).do { |i|
						if(curves[i].isNumber and: {
							Rect.aboutPoint(
								this.scaleNormalPointToView(Point(
									0.5 * (viewCoords[0][i] + viewCoords[0][i+1]),
									this.midpointYForCurve(i)
								)),
								curveZoneWidth * 0.5, curveZoneWidth * 0.5
							).containsPoint(mouse)
						}) {
							break.(i)
						};
					};
					nil
				};
				if(trackingCurveIndex.isNil) {
					trackingPointIndex = this.findIndexOfClickedPoint(Point(x, y));
				};
			};
		};
		envView.mouseMoveAction = { |view, x, y, modifiers|
			var mouse, left, right;
			modifiers = modifiers & 0x001E0000;
			case
			{ trackingCurveIndex.notNil } {
				mouse = this.scaleViewPointToNormal(Point(x, y));
				left = viewCoords[1][trackingCurveIndex];
				right = viewCoords[1][trackingCurveIndex + 1];
				if(mouse.y.exclusivelyBetween(min(left, right), max(left, right))) {
					curves[trackingCurveIndex] = this.curveForMidpointY(
						trackingCurveIndex, mouse.y
					);
					this.refresh;
					curveAction.value(this, curves);
				};
			};
		};
		// handles ctrl-click 
		envView.mouseUpAction = { |view, x, y, modifiers|
			var viewValue = view.value,
			realExtent = view.bounds.extent,
			extent = realExtent - thumbSize,
			mouse = Point(x, y),
			halfThumbSize = Point(thumbSize, thumbSize) * 0.5,  // half, for aboutPoint
			thumbRect, index;
			modifiers = modifiers & 0x001E0000;
			case
			{ trackingCurveIndex.notNil } {
				trackingCurveIndex = nil
			}
			{ trackingPointIndex.notNil } {
				trackingPointIndex = nil
			}
			{ modifiers == 262144 } {
				index = this.findIndexOfClickedPoint(mouse);
				if(index.notNil) {
					this.deletePoint(index);
				} {
					index = block { |break|
						(viewValue[0] * extent.x + halfThumbSize.x).doAdjacentPairs { |a, b, i|
							if(x.inclusivelyBetween(a, b)) { break.(i) };
						};
						break.(viewValue[0].size);
					};
					this.insertPoint(index+1,
						(x - halfThumbSize.x) / extent.x,
						(realExtent.y - y - halfThumbSize.y) / extent.y,
						curves[index]
					);
				};
			};
			this.refresh;
			if(GUI.id != \cocoa and: { (envView.index ? -1) > 0 }) {
				envView.deselectIndex(envView.index);
			};
		};

		// enables ctrl-modifier not to turn into a drag
		envView.beginDragAction = { nil };
	}

	background_ { |color|
		background = color;
		userView.background = color;
	}
	env_ { |env|
		currentEnvironment.put(\envel, env);
		releaseNode = env.releaseNode;
		loopNode = env.loopNode;
		viewCoords = [(#[0] ++ env.times.normalizeSum).integrate, env.levels];
		this.curves = env.curves;
		this.refresh;
	}
	value {
		^Env(viewCoords[1],
			viewCoords[0].differentiate.drop(1),
			curves.drop(-1), releaseNode, loopNode
		)
	}
	doAction { action.value(this) }

	curves_ { |newCurves|
		// really this should be 'size - 1' but there's a bug in qt
		if(newCurves.isArray.not) {
			curves = newCurves.dup(viewCoords[0].size);
		} {
			curves = newCurves.extend(viewCoords[0].size, 0);
		};
		this.refresh;
	}
	releaseNode_ { |index|
		releaseNode = index;
		this.refresh;
	}
	loopNode_ { |index|
		loopNode = index;
		this.refresh;
	}
	loopNodeColor_ { |color|
		loopNodeColor = color;
		this.refresh;
	}
	releaseNodeColor_ { |color|
		releaseNodeColor = color;
		this.refresh;
	}
	fillColor_ { |color|
		fillColor = color;
		this.refresh;
	}
	curveZoneColor_ { |color|
		curveZoneColor = color;
		this.refresh;
	}
	curveZoneWidth_ { |pixels|
		curveZoneWidth = pixels;
		this.refresh;
	}
	thumbSize_ { |pixels|
		thumbSize = pixels;
		this.refresh;
	}

	insertPoint { |index, time, level, curve|
		this.fixSpecialNodes(1, index);
		// Swing doesn't support valueAction_ -- F-U-U-U-U-U-U-
		envView.value_([
			viewCoords[0].insert(index, time),
			viewCoords[1].insert(index, level)
		]).doAction;
		curves = curves.insert(index, curve ?? { curves[index] });
		envView.curves = curves;
		insertAction.value(this);
	}
	deletePoint { |index|
		this.fixSpecialNodes(-1, index);
		envView.value_(viewCoords.collect { |row|
			row.removeAt(index); row
		}).doAction;
		curves.removeAt(index);
		envView.curves = curves;
		deleteAction.value(this);
	}
	refresh {
		this.updateEnvView;
		// for swing:
		// in qt, refreshing a composite view redraws all its children
		// in swing, a userview inside a compositeview doesn't get redrawn...
		view.children.do(_.refresh);
	}
	updateEnvView {
		envView.value_(viewCoords)
		.curves_(curves)
		.thumbSize_(thumbSize)
		.fillColor_(fillColor)
		.strokeColor_(fillColor);
		viewCoords[0].size.do { |i|
			if(i >= (releaseNode ? 1e10)) {
				envView.setFillColor(i, releaseNodeColor)
			} {
				if(i >= (loopNode ? 1e10)) {
					envView.setFillColor(i, loopNodeColor)
				} {
					envView.setFillColor(i, fillColor);
				}
			};
		};
	}
	fixSpecialNodes { |incr, index|
		if(releaseNode.notNil and: { releaseNode >= index}) {
			releaseNode = releaseNode + incr
		};
		if(loopNode.notNil and: { loopNode >= index }) {
			loopNode = loopNode + incr
		};
	}
	remove {
		view.remove;
	}
	scaleNormalPointToView { |point|
		^Point(point.x, 1.0 - point.y) * (view.bounds.extent - thumbSize) + (thumbSize * 0.5)
	}
	scaleViewPointToNormal { |point|
		^(Point(point.x, view.bounds.height - point.y) - (thumbSize * 0.5))
		/ (view.bounds.extent - thumbSize)
	}
	midpointYForCurve { |index|
		if(curves[index].isNumber) {
			^ControlSpec(viewCoords[1][index], viewCoords[1][index+1], curves[index])
			.map(0.5)
		} {
			^(viewCoords[1][index] + viewCoords[1][index+1]) * 0.5
		}
	}
	curveForMidpointY { |index, midval|
		var minval = viewCoords[1][index],
		maxval = viewCoords[1][index + 1],
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
			^log(qresult.squared).abs * sgn
		}, {
			^log(((b.neg - sqrterm) / (2 * a)).squared).abs * sgn
		});
	}
	findIndexOfClickedPoint { |pointInPixels|
		var halfThumbSize = Point(thumbSize, thumbSize) * 0.5;
		envView.value.flop.do { |pair, i|
			if(Rect.aboutPoint(this.scaleNormalPointToView(Point(*pair)),
				halfThumbSize.x, halfThumbSize.y).containsPoint(pointInPixels)) { ^i };
		};
		^nil
	}
}