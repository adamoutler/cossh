import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

val width = 1080
val height = 1920
val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
val g2d = image.createGraphics()

g2d.color = Color.BLACK
g2d.fillRect(0, 0, width, height)

g2d.font = Font("Monospaced", Font.PLAIN, 40)
g2d.color = Color.WHITE

val text = """
Welcome to CoSSH Terminal
user@test-server:~$ top

Tasks: 1 total,   1 running,   0 sleeping,   0 stopped,   0 zombie
%Cpu(s):  0.0 us,  0.0 sy,  0.0 ni,100.0 id,  0.0 wa,  0.0 hi,  0.0 si,  0.0 st
MiB Mem :   7950.4 total,   5314.9 free,   1101.4 used,   1534.1 buff/cache
MiB Swap:   4096.0 total,   4096.0 free,      0.0 used.   6543.8 avail Mem

  PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
    1 user      20   0    2932   1576   1408 R   0.0   0.0   0:00.00 top
""".trimIndent()

var y = 100
for (line in text.split("\n")) {
    g2d.drawString(line, 20, y)
    y += 50
}

g2d.dispose()

val dir = File("app/src/test/snapshots/images")
dir.mkdirs()
ImageIO.write(image, "png", File(dir, "live_terminal_actual.png"))
