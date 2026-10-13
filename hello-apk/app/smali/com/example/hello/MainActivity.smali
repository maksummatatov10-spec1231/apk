.class public Lcom/example/hello/MainActivity;
.super Landroid/app/Activity;
.source "MainActivity.java"

# Эквивалент Java:
#   public class MainActivity extends Activity {
#       protected void onCreate(Bundle b) {
#           super.onCreate(b);
#           TextView tv = new TextView(this);
#           tv.setText("Привет из APK!");
#           setContentView(tv);
#       }
#   }

.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Landroid/app/Activity;-><init>()V
    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .registers 4
    .param p1, "savedInstanceState"    # Landroid/os/Bundle;

    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    new-instance v0, Landroid/widget/TextView;
    invoke-direct {v0, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V

    const-string v1, "Привет из APK!"
    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {p0, v0}, Landroid/app/Activity;->setContentView(Landroid/view/View;)V
    return-void
.end method
