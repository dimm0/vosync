package edu.jhu.pha.vosync;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Desktop;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.WebAuthSession.WebAuthInfo;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.RowSpec;

public class SyncFolderChooser extends JDialog {

	private static final long serialVersionUID = 5831105006158898474L;
	private final JPanel contentPanel = new JPanel();
	private JTextField syncDirField;
	private JTextField oauthTokenField;
	private JTextField oauthSecretField;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			SyncFolderChooser dialog = new SyncFolderChooser();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public SyncFolderChooser() {
		setBounds(100, 100, 642, 205);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));
		{
			JPanel panel = new JPanel();
			FlowLayout flowLayout_1 = (FlowLayout) panel.getLayout();
			flowLayout_1.setAlignment(FlowLayout.LEFT);
			contentPanel.add(panel, BorderLayout.NORTH);
			{
				JLabel syncChooseLabel = new JLabel("Choose sync folder:");
				panel.add(syncChooseLabel);
			}
			{
				syncDirField = new JTextField();
				panel.add(syncDirField);
				syncDirField.setEditable(false);
				syncDirField.setText(VOSync.getPrefs().get("syncdir",""));
				syncDirField.setColumns(30);
			}
			{
				JButton chooseButton = new JButton("Choose...");
				panel.add(chooseButton);
				{
					JPanel panel_1 = new JPanel();
					contentPanel.add(panel_1, BorderLayout.SOUTH);
					panel_1.setLayout(new FormLayout(new ColumnSpec[] {
							ColumnSpec.decode("left:min"),
							FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
							ColumnSpec.decode("pref:grow"),},
						new RowSpec[] {
							RowSpec.decode("28px"),
							FormFactory.RELATED_GAP_ROWSPEC,
							RowSpec.decode("28px"),}));
					{
						JLabel oauthTokenLabel = new JLabel(" OAuth token: ");
						panel_1.add(oauthTokenLabel, "1, 1, fill, fill");
					}
					{
						oauthTokenField = new JTextField();
						panel_1.add(oauthTokenField, "3, 1, fill, fill");
						oauthTokenField.setColumns(10);
						oauthTokenField.setText(VOSync.getPrefs().get("oauthToken",""));
					}
					{
						JLabel oauthSecretLabel = new JLabel(" OAuth secret: ");
						panel_1.add(oauthSecretLabel, "1, 3, fill, fill");
					}
					{
						oauthSecretField = new JTextField();
						panel_1.add(oauthSecretField, "3, 3, fill, fill");
						oauthSecretField.setColumns(10);
						oauthSecretField.setText(VOSync.getPrefs().get("oauthSecret",""));
					}
				}
				{
					JPanel panel_1 = new JPanel();
					contentPanel.add(panel_1, BorderLayout.CENTER);
					
					final JButton btnReceiveOauthCredentials = new JButton("Authenticate");
					panel_1.add(btnReceiveOauthCredentials);

					final JButton btnContinue = new JButton("Continue");
					btnContinue.setEnabled(false);
					panel_1.add(btnContinue);

					btnReceiveOauthCredentials.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) {
							try {
								VOSync.initSession(true);
								final WebAuthInfo info = VOSync.getSession().getAuthInfo();

								try {
									Desktop.getDesktop().browse(URI.create(VOSync.getServiceUrl()+"/authorize?provider=vao&action=initiate&oauth_token="+info.requestTokenPair.key));
								} catch (IOException e1) {
									e1.printStackTrace();
								}
								
								btnReceiveOauthCredentials.setEnabled(false);
								btnContinue.setEnabled(true);
								btnContinue.addMouseListener(new MouseAdapter() {
									@Override
									public void mouseClicked(MouseEvent e) {
										try {
											VOSync.getSession().retrieveWebAccessToken(info.requestTokenPair);
											oauthTokenField.setText(VOSync.getSession().getAccessTokenPair().key);
											oauthSecretField.setText(VOSync.getSession().getAccessTokenPair().secret);
										} catch (DropboxException e1) {
											// TODO Auto-generated catch block
											e1.printStackTrace();
										}
									}
								});
							} catch (DropboxException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					});

				}
				chooseButton.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						JFileChooser chooser = new JFileChooser();
						chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					    int returnVal = chooser.showOpenDialog(null);
					    if(returnVal == JFileChooser.APPROVE_OPTION) {
					    	syncDirField.setText(chooser.getSelectedFile().getAbsolutePath());
					    }
					}
				});
			}
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton okButton = new JButton("OK");
				okButton.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
					    VOSync.setSyncDir(Paths.get(syncDirField.getText()).toString());
					    VOSync.setCredentials(oauthTokenField.getText(), oauthSecretField.getText());

					    VOSync.getInstance().init();
					    setVisible(false);
					}
				});
				okButton.setActionCommand("OK");
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
			{
				JButton cancelButton = new JButton("Cancel");
				cancelButton.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						dispose();
					}
				});
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
		}
	}

}
