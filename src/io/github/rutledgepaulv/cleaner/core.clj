(ns io.github.rutledgepaulv.cleaner.core
  (:require [clojure.java.io :as io])
  (:import (clojure.lang IReduceInit)
           (java.awt Color Graphics2D)
           (java.awt.image BufferedImage BufferedImageOp)
           (java.io ByteArrayOutputStream File)
           (javax.imageio ImageIO)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox.pdmodel.common PDRectangle)
           (org.apache.pdfbox.pdmodel.graphics.image LosslessFactory)
           (org.apache.pdfbox.rendering ImageType PDFRenderer)
           (org.imgscalr Scalr Scalr$Method))
  (:gen-class))

(def DPI 300)
(def DEFAULT_DPI (float 72))
(def CANVAS_WIDTH (* DPI 8.5))
(def CANVAS_HEIGHT (* DPI 11))

(defn pdf->images [input]
  (reify IReduceInit
    (reduce [this f init]
      (with-open [document ^PDDocument (Loader/loadPDF input)]
        (let [renderer (PDFRenderer. document)]
          (reduce
            (fn [agg idx]
              (f agg (.renderImage renderer idx (/ DPI DEFAULT_DPI) ImageType/BINARY)))
            init
            (range (.getCount (.getPages document)))))))))

(defn get-cell [^BufferedImage image x1 y1 x2 y2]
  (let [clamp-x1 (int (min (max x1 0) (.getWidth image)))
        clamp-y1 (int (min (max y1 0) (.getHeight image)))
        clamp-x2 (int (min (max x2 0) (.getWidth image)))
        clamp-y2 (int (min (max y2 0) (.getHeight image)))
        width    (- clamp-x2 clamp-x1)
        height   (- clamp-y2 clamp-y1)]
    (.getRGB image clamp-x1 clamp-y1 width height nil 0 width)))

(defn average-color [x]
  (/ (reduce +' 0 (map {-1 -1 -16777216 0} x)) (float (count x))))

(defn grid [^BufferedImage image x-resolution y-resolution]
  (let [width       (.getWidth image)
        height      (.getHeight image)
        cell-width  (/ width x-resolution)
        cell-height (/ height y-resolution)]
    (for [x (range 0 x-resolution)
          y (range 0 y-resolution)]
      (let [cell (get-cell image
                           (* x cell-width)
                           (* y cell-height)
                           (* (inc x) cell-width)
                           (* (inc y) cell-height))]
        {:x x :y y :color (average-color cell)}))))

(defn has-content? [min x]
  (< -0.97 x 0))

(defn corners
  "Given a list of coordinates, return a single map describing the corners."
  [coordinates]
  (reduce
    (fn [{:keys [min-x max-x min-y max-y]} {:keys [x y]}]
      {:min-x (if (< x min-x) x min-x)
       :max-x (if (< max-x x) x max-x)
       :min-y (if (< y min-y) y min-y)
       :max-y (if (< max-y y) y max-y)})
    {:min-x Integer/MAX_VALUE
     :min-y Integer/MAX_VALUE
     :max-x Integer/MIN_VALUE
     :max-y Integer/MIN_VALUE}
    coordinates))

(defn crop-to-content
  ([^BufferedImage image]
   (crop-to-content image 20 20))
  ([^BufferedImage image x-resolution y-resolution]
   (let [{:keys [min-x max-x min-y max-y]}
         (->> (grid image x-resolution y-resolution)
              (filter (fn [{:keys [x y color]}]
                        (< -0.999 color)))
              (corners))
         x1     (* min-x (Math/floor (double (/ (.getWidth image) x-resolution))))
         y1     (* min-y (Math/floor (double (/ (.getHeight image) y-resolution))))
         x2     (* (inc max-x) (Math/floor (double (/ (.getWidth image) x-resolution))))
         y2     (* (inc max-y) (Math/floor (double (/ (.getHeight image) y-resolution))))
         width  (- x2 x1)
         height (- y2 y1)]
     (Scalr/crop image x1 y1 width height (into-array BufferedImageOp [Scalr/OP_ANTIALIAS Scalr/OP_GRAYSCALE])))))

(defn maximize-content [^BufferedImage image]
  (Scalr/resize image Scalr$Method/ULTRA_QUALITY (int CANVAS_WIDTH) (int CANVAS_HEIGHT) (into-array BufferedImageOp [])))

(defn write-image [^BufferedImage image output-file]
  (ImageIO/write image "png" (io/file output-file)))

(defn center-on-canvas [^BufferedImage image]
  (let [canvas   (BufferedImage. CANVAS_WIDTH CANVAS_HEIGHT BufferedImage/TYPE_INT_ARGB)
        x-offset (Math/floor (double (/ (- CANVAS_WIDTH (.getWidth image)) 2.0)))
        y-offset (Math/floor (double (/ (- CANVAS_HEIGHT (.getHeight image)) 2.0)))
        graphics ^Graphics2D (.getGraphics canvas)]
    (.setBackground graphics Color/WHITE)
    (.drawImage graphics image nil (int x-offset) (int y-offset))
    canvas))

(defn cleanup-image [^BufferedImage x]
  (-> x
      (crop-to-content)
      (maximize-content)
      (center-on-canvas)))

(defn images->pdf [images-reducible]
  (reduce
    (fn [^PDDocument doc ^BufferedImage image]
      (let [page (PDPage. (PDRectangle. (.getWidth image) (.getHeight image)))]
        (with-open [stream (PDPageContentStream. doc page)]
          (.drawImage stream (LosslessFactory/createFromImage doc image) (float 0) (float 0)))
        (.addPage doc page)
        doc))
    (PDDocument.)
    images-reducible))

(defn convert [input]
  (let [doc ^PDDocument
            (->> (pdf->images input)
                 (eduction (map cleanup-image))
                 (images->pdf))]
    (.save doc System/out)))

(defn -main [& args]
  (let [bites (ByteArrayOutputStream.)]
    (with-open [in System/in] (io/copy in bites))
    (convert (.toByteArray bites))))

