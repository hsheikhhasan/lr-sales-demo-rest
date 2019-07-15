package com.liferay.sales.demo.rest.application;

import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.util.Arrays;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Application;

import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONFactoryUtil;

import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;

import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.xml.Node;
import com.liferay.portal.kernel.xml.Document;

import com.liferay.journal.service.JournalArticleLocalService;

import com.liferay.dynamic.data.mapping.kernel.DDMStructure;
import com.liferay.dynamic.data.mapping.kernel.DDMStructureManagerUtil;

import com.liferay.journal.model.JournalArticle;


import com.liferay.portal.kernel.model.PortletPreferences;
import com.liferay.portal.kernel.service.PortletPreferencesLocalServiceUtil;

/**
 * @author hhasan
 */
@Component(
	immediate = true,
	property = {
		"liferay.auth.verifier=false",
		"liferay.oauth2=false",
		"osgi.jaxrs.application.base=" + "/salesdemo-rest",
		"osgi.jaxrs.name=SalesDemo.Rest"
	},
	service = Application.class
)
public class SalesDemoRestApplication extends Application {

	public Set<Object> getSingletons() {
		return Collections.<Object>singleton(this);
	}

	@GET
	@Path("/preferences")
	@Produces({
		MediaType.APPLICATION_JSON
	})
	public String getPortletPreferences() {
		List<PortletPreferences> portletPreferences = PortletPreferencesLocalServiceUtil.getPortletPreferences();
		
		for (PortletPreferences preference : portletPreferences) {			
			if (preference.getPreferences().contains("vocabularyId")) {
				String[] prefs = preference.getPreferences().replaceAll("<portlet-preferences><preference>", "")
					.replaceAll("</preference></portlet-preferences>", "")
					.replaceAll("</preference><preference>", "")
					.split("</value>");

				JSONArray jsonArray = JSONFactoryUtil.createJSONArray();
				jsonArray.put(JSONFactoryUtil.createJSONObject().put("vocabularyId", prefs[0].substring(prefs[0].indexOf("<value>")).replace("<value>", "")));
				jsonArray.put(JSONFactoryUtil.createJSONObject().put("structureId", prefs[2].substring(prefs[2].indexOf("<value>")).replace("<value>", "")));
				jsonArray.put(JSONFactoryUtil.createJSONObject().put("apiType", prefs[1].substring(prefs[1].indexOf("<value>")).replace("<value>", "")));
				jsonArray.put(JSONFactoryUtil.createJSONObject().put("preferredLocation", prefs[3].substring(prefs[3].indexOf("<value>")).replace("<value>", "").split("-")[0]));
				jsonArray.put(JSONFactoryUtil.createJSONObject().put("preferredRegion", prefs[3].substring(prefs[3].indexOf("<value>")).replace("<value>", "").split("-")[1]));
				return jsonArray.toString();
			}			
		}
		return "{}";
	}

	@GET
	@Path("/categories/{vocabularyName}")
	@Produces({
		MediaType.APPLICATION_JSON
	})
	public String getCategories(@PathParam("vocabularyName") String vocabularyName) {
		List<AssetVocabulary> vocabularies =
			 _assetVocabularyLocalService.getAssetVocabularies(0, _assetVocabularyLocalService.getAssetVocabulariesCount());
		
		AssetVocabulary assetVocabulary = null;
		for(AssetVocabulary vocabulary : vocabularies) {
			if (vocabulary.getName().equalsIgnoreCase(vocabularyName)) {
				assetVocabulary = vocabulary;
				break;
			}
		}
		return JSONFactoryUtil.looseSerialize(assetVocabulary.getCategories());	
	}

	@GET
	@Path("/articles/{structureId}/{locationName}")
	@Produces({
		MediaType.APPLICATION_JSON
	})
	public String getStructureJournalArticles(@PathParam("structureId") String structureId,
			@PathParam("locationName") String locationName) {
		try {
			DDMStructure dDMStructure = DDMStructureManagerUtil.getStructure(Long.parseLong(structureId));
			if (dDMStructure != null) {			 			
				String[] dDMStructureKeys = {dDMStructure.getStructureKey()};
				List<JournalArticle> journalArticles =
				 	_journalArticleLocalService.getStructureArticles(dDMStructureKeys);
			
				if (journalArticles != null) {
					JSONArray jsonArray = JSONFactoryUtil.createJSONArray();
					for (JournalArticle journalArticle : journalArticles) {
						if (_journalArticleLocalService.isLatestVersion(
								journalArticle.getGroupId(), 
								journalArticle.getArticleId(), 
								journalArticle.getVersion())) {
							
							JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
							jsonObject.put("id", journalArticle.getArticleId());
							jsonObject.put("title", journalArticle.getTitle());
							jsonObject.put("content", 
								getJournalArticleCustomFieldValue(journalArticle, "content"));

							String[] categoryNames = _assetCategoryLocalService.getCategoryNames(
									JournalArticle.class.getName(), 
									journalArticle.getResourcePrimKey());

							jsonObject.put("location", categoryNames.length > 0 ? Arrays.toString(categoryNames) : "");

							if (locationName.equalsIgnoreCase("all")
									|| Arrays.toString(categoryNames).toLowerCase().contains(locationName.toLowerCase()) ) {
								jsonArray.put(jsonObject);
							}

							jsonObject.put("documents", 
								getJournalArticleCustomFieldValue(journalArticle, "documents"));
						} 	
					}
					return jsonArray.toString();
				}	
			}			 
		} catch(Exception e) {	
			return "{}";
		}
		return "{}";
	}

	private static String getJournalArticleCustomFieldValue(JournalArticle journalArticle, String customFieldName) {
		try {
			Document document = SAXReaderUtil.read(journalArticle.getContent());
			if (document != null) {
				Node node = document.selectSingleNode(
					"/root/dynamic-element[@name='" + customFieldName + "']/dynamic-content");
					if (node != null) {
						return node.getText();
					}
			}
		} catch(Exception e) {
		}
		return null;
	}
	
	@Reference
	private AssetVocabularyLocalService _assetVocabularyLocalService;

	@Reference
	private JournalArticleLocalService _journalArticleLocalService; 

	@Reference
	AssetCategoryLocalService _assetCategoryLocalService;
}