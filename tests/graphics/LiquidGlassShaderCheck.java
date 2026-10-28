import android.graphics.*;
import android.media.Image;
import android.media.ImageReader;
import android.hardware.HardwareBuffer;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class LiquidGlassShaderCheck {
  static byte[] render(String source, int width, int height, float radius, float thickness, float intensity) throws Exception {
    RuntimeShader shader = new RuntimeShader(source);
    shader.setFloatUniform("resolution", width, height);
    shader.setFloatUniform("center", width / 2f, height / 2f);
    shader.setFloatUniform("size", width / 2f, height / 2f);
    shader.setFloatUniform("radius", radius, radius, radius, radius);
    shader.setFloatUniform("thickness", thickness);
    shader.setFloatUniform("refract_index", 1.5f);
    shader.setFloatUniform("refract_intensity", intensity);
    shader.setFloatUniform("foreground_color_premultiplied", .02f, .03f, .04f, .1f);
    RenderNode content = new RenderNode("GlassRegressionContent");
    content.setPosition(0, 0, width, height);
    content.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"));
    RecordingCanvas canvas = content.beginRecording(width, height);
    Paint paint = new Paint();
    for (int y = 0; y < height; y += 8) {
      for (int x = 0; x < width; x += 8) {
        paint.setColor(Color.rgb((x * 13 + y * 3) & 255, (x * 7 + y * 11) & 255, (x * 5 + y * 17) & 255));
        canvas.drawRect(x, y, Math.min(width, x + 8), Math.min(height, y + 8), paint);
      }
    }
    content.endRecording();
    ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_CPU_READ_OFTEN);
    HardwareRenderer renderer = new HardwareRenderer();
    try {
      renderer.setSurface(reader.getSurface());
      renderer.setContentRoot(content);
      renderer.setOpaque(false);
      int status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
      Image image = reader.acquireLatestImage();
      if (image == null) throw new AssertionError("No GPU output, status=" + status);
      try {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        byte[] pixels = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
          for (int x = 0; x < width; x++) {
            int from = y * plane.getRowStride() + x * plane.getPixelStride();
            int to = (y * width + x) * 4;
            for (int c = 0; c < 4; c++) pixels[to + c] = buffer.get(from + c);
          }
        }
        return pixels;
      } finally { image.close(); }
    } finally {
      renderer.destroy();
      reader.close();
      content.discardDisplayList();
    }
  }
  public static void main(String[] args) throws Exception {
    String original = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
    String optimized = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
    float[][] cases = {{1040, 136, 62, 30, 1}, {132, 132, 66, 25, 1},
        {640, 160, 40, 20, 1}, {640, 160, 40, 20, 0}, {320, 96, 48, 1, 1}};
    for (float[] c : cases) {
      byte[] before = render(original, (int)c[0], (int)c[1], c[2], c[3], c[4]);
      byte[] after = render(optimized, (int)c[0], (int)c[1], c[2], c[3], c[4]);
      int max = 0, different = 0;
      for (int i = 0; i < before.length; i++) {
        int delta = Math.abs((before[i] & 255) - (after[i] & 255));
        max = Math.max(max, delta);
        if (delta != 0) different++;
      }
      System.out.println((int)c[0] + "x" + (int)c[1] + " thickness=" + c[3] + " intensity=" + c[4]
          + " maxChannelDifference=" + max + " differentChannels=" + different);
      if (max > 1) throw new AssertionError("GPU rendering differs by more than one color level");
    }
    System.out.println("GPU shader compilation and image equivalence checks passed.");
  }
}
