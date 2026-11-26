package main

import (
	"errors"
	"image"
	"math"
	"slices"
)

type ircPoint struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
}

type ircQuad [4]ircPoint // top left, top right, bottom right, bottom left
type ircHomography [8]float64

func ircCross(a, b, c ircPoint) float64 {
	return (b.X-a.X)*(c.Y-a.Y) - (b.Y-a.Y)*(c.X-a.X)
}

func ircProject(m ircHomography, p ircPoint) (ircPoint, bool) {
	den := m[6]*p.X + m[7]*p.Y + 1
	if math.Abs(den) < 1e-8 {
		return ircPoint{}, false
	}
	q := ircPoint{(m[0]*p.X + m[1]*p.Y + m[2]) / den, (m[3]*p.X + m[4]*p.Y + m[5]) / den}
	return q, !math.IsNaN(q.X) && !math.IsNaN(q.Y) && !math.IsInf(q.X, 0) && !math.IsInf(q.Y, 0)
}

func ircFitPerspective(from, to ircQuad) (ircHomography, error) {
	var a [8][9]float64
	for i, p := range from {
		q := to[i]
		a[2*i] = [9]float64{p.X, p.Y, 1, 0, 0, 0, -q.X * p.X, -q.X * p.Y, q.X}
		a[2*i+1] = [9]float64{0, 0, 0, p.X, p.Y, 1, -q.Y * p.X, -q.Y * p.Y, q.Y}
	}
	for col := range a {
		pivot := col
		for row := col + 1; row < len(a); row++ {
			if math.Abs(a[row][col]) > math.Abs(a[pivot][col]) {
				pivot = row
			}
		}
		a[col], a[pivot] = a[pivot], a[col]
		d := a[col][col]
		if math.Abs(d) < 1e-8 {
			return ircHomography{}, errors.New("calibration corners are degenerate")
		}
		for j := col; j < 9; j++ {
			a[col][j] /= d
		}
		for row := range a {
			if row != col {
				k := a[row][col]
				for j := col; j < 9; j++ {
					a[row][j] -= k * a[col][j]
				}
			}
		}
	}
	var m ircHomography
	for i := range m {
		m[i] = a[i][8]
	}
	return m, nil
}

func ircSignedArea(points []ircPoint) float64 {
	area := 0.0
	for i, p := range points {
		q := points[(i+1)%len(points)]
		area += p.X*q.Y - p.Y*q.X
	}
	return area / 2
}

// Detect a filled, dark quadrilateral with light margins on all four sides.
// Work in reduced pixel space to bound memory, including for high-res monitors.
func ircFindRectangle(img image.Image) (ircQuad, error) {
	b := img.Bounds()
	step := max(1, (max(b.Dx(), b.Dy())+959)/960)
	w, h := b.Dx()/step, b.Dy()/step
	if w < 100 || h < 100 {
		return ircQuad{}, errors.New("calibration image too small")
	}
	gray := make([]uint8, w*h)
	for y := range h {
		for x := range w {
			r, g, bl, _ := img.At(b.Min.X+x*step+step/2, b.Min.Y+y*step+step/2).RGBA()
			gray[y*w+x] = uint8((77*r + 150*g + 29*bl) >> 16)
		}
	}
	for _, threshold := range []uint8{104, 144, 64, 184} {
		q, found, ambiguous := ircDarkQuad(gray, w, h, threshold)
		if ambiguous {
			return ircQuad{}, errors.New("multiple dark rectangles found; use one calibration rectangle")
		}
		if found {
			for i, p := range q {
				q[i] = ircPoint{(p.X*float64(step) + float64(step/2) + .5) / float64(b.Dx()), (p.Y*float64(step) + float64(step/2) + .5) / float64(b.Dy())}
			}
			return q, nil
		}
	}
	return ircQuad{}, errors.New("no large dark rectangle with a clear light margin found")
}

func ircDarkQuad(gray []uint8, w, h int, threshold uint8) (ircQuad, bool, bool) {
	seen := make([]bool, len(gray))
	var queue []int
	left, right := make([]int, h), make([]int, h)
	minSize := w * h / 25
	var best ircQuad
	bestArea := 0.0
	for i, g := range gray {
		if g > threshold || seen[i] {
			continue
		}
		queue = append(queue[:0], i)
		seen[i] = true
		touchesBorder := false
		for head := 0; head < len(queue); head++ {
			p := queue[head]
			x, y := p%w, p/w
			touchesBorder = touchesBorder || x < 2 || x >= w-2 || y < 2 || y >= h-2
			for _, n := range [...]int{p - 1, p + 1, p - w, p + w} {
				if n < 0 || n >= len(gray) || absInt(n%w-x) > 1 || seen[n] || gray[n] > threshold {
					continue
				}
				seen[n] = true
				queue = append(queue, n)
			}
		}
		if touchesBorder || len(queue) < minSize {
			continue
		}
		for y := range h {
			left[y], right[y] = w, -1
		}
		for _, p := range queue {
			x, y := p%w, p/w
			left[y] = min(left[y], x)
			right[y] = max(right[y], x)
		}
		var points []ircPoint
		for y, x := range left {
			if x <= right[y] {
				points = append(points, ircPoint{float64(x), float64(y)}, ircPoint{float64(right[y]), float64(y)})
			}
		}
		q, area, ok := ircFitQuad(points, len(queue))
		if !ok || !ircHasLightMargin(q, gray, w, h, threshold) {
			continue
		}
		if bestArea > 0 {
			return ircQuad{}, false, true
		}
		best, bestArea = q, area
	}
	return best, bestArea > 0, false
}

func ircFitQuad(points []ircPoint, size int) (ircQuad, float64, bool) {
	slices.SortFunc(points, func(a, b ircPoint) int {
		if a.X == b.X {
			return int(a.Y - b.Y)
		}
		return int(a.X - b.X)
	})
	var hull []ircPoint
	add := func(p ircPoint, start int) {
		for len(hull) >= start+2 && ircCross(hull[len(hull)-2], hull[len(hull)-1], p) <= 0 {
			hull = hull[:len(hull)-1]
		}
		hull = append(hull, p)
	}
	for _, p := range points {
		add(p, 0)
	}
	if len(hull) < 2 {
		return ircQuad{}, 0, false
	}
	hull = hull[:len(hull)-1]
	split := len(hull)
	for i := len(points) - 1; i >= 0; i-- {
		add(points[i], split)
	}
	hull = hull[:len(hull)-1]
	area := ircSignedArea(hull)
	if len(hull) < 4 || area < .94*float64(size) || float64(size) < .90*area {
		return ircQuad{}, 0, false
	}
	for len(hull) > 4 {
		smallest, index := math.Inf(1), 0
		for i, p := range hull {
			cost := ircCross(hull[(i+len(hull)-1)%len(hull)], p, hull[(i+1)%len(hull)])
			if cost < smallest {
				smallest, index = cost, i
			}
		}
		hull = slices.Delete(hull, index, index+1)
	}
	if ircSignedArea(hull) < .96*area {
		return ircQuad{}, 0, false
	}
	start := 0
	for i, p := range hull {
		if p.X+p.Y < hull[start].X+hull[start].Y {
			start = i
		}
	}
	var q ircQuad
	for i := range q {
		q[i] = hull[(start+i)%4]
	}
	for i, p := range q {
		d := q[(i+1)%4]
		if ircCross(p, d, q[(i+2)%4]) < .15*area {
			return ircQuad{}, 0, false
		}
	}
	// An upright image is required to disambiguate the four identical corners.
	if q[1].X <= q[0].X || q[2].Y <= q[1].Y || q[3].X >= q[2].X || q[0].Y >= q[3].Y {
		return ircQuad{}, 0, false
	}
	return q, area, true
}

func ircHasLightMargin(q ircQuad, gray []uint8, w, h int, threshold uint8) bool {
	distance := float64(max(3, min(w, h)/80))
	for i, a := range q {
		b := q[(i+1)%4]
		dx, dy := b.X-a.X, b.Y-a.Y
		length := math.Hypot(dx, dy)
		for _, fraction := range []float64{.2, .4, .6, .8} {
			x := int(math.Round(a.X + dx*fraction + dy/length*distance))
			y := int(math.Round(a.Y + dy*fraction - dx/length*distance))
			ix := int(math.Round(a.X + dx*fraction - dy/length*distance))
			iy := int(math.Round(a.Y + dy*fraction + dx/length*distance))
			if x < 0 || y < 0 || x >= w || y >= h || ix < 0 || iy < 0 || ix >= w || iy >= h ||
				int(gray[y*w+x])-int(gray[iy*w+ix]) < 40 || gray[iy*w+ix] > threshold {
				return false
			}
		}
	}
	return true
}

func absInt(n int) int {
	if n < 0 {
		return -n
	}
	return n
}
